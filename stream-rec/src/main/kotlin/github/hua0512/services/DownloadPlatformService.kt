/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.data.config.engine.DownloadEngines
import github.hua0512.data.config.engine.EngineConfig
import github.hua0512.data.event.StreamerEvent.StreamerException
import github.hua0512.data.event.StreamerEvent.StreamerRecordStop
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.download.base.IPlatformDownloaderFactory
import github.hua0512.plugins.download.base.StreamerCallback
import github.hua0512.plugins.download.fillDownloadConfig
import github.hua0512.plugins.download.globalConfig
import github.hua0512.plugins.event.EventCenter
import github.hua0512.repo.config.EngineConfigManager
import github.hua0512.services.DownloadPlatformService.StreamerState.StreamerStatus.*
import github.hua0512.utils.RateLimiter
import github.hua0512.utils.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.DurationUnit
import kotlin.time.toDuration


/**
 * Download platform service, used to download streamers from the same platform
 *
 *
 * @param app The application instance
 * @param scope Coroutine scope to launch download jobs
 * @param semaphore Semaphore to limit the number of concurrent downloads
 * @param fetchDelay Delay between adding streamers
 */
class DownloadPlatformService(
  val app: App,
  private val scope: CoroutineScope,
  private var fetchDelay: Long = 0,
  private val semaphore: Semaphore,
  private val callback: StreamerCallback,
  private val platform: StreamingPlatform,
  private val downloadFactory: IPlatformDownloaderFactory,
  private val downloadEngineConfigManager: EngineConfigManager,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = logger(DownloadPlatformService::class.java)
  }

  private data class StreamerState(
    val streamer: Streamer,
    var state: StreamerStatus,
    var downloader: StreamerDownloadService? = null,
  ) {
    enum class StreamerStatus {
      PENDING,
      RESERVED,
      DOWNLOADING,
      CANCELLED
    }
  }

  private val stateMutex = Mutex()
  private val streamerStates = ConcurrentHashMap<String, StreamerState>()
  private val streamerChannel = Channel<Streamer>(Channel.BUFFERED)

  private val rateLimiter = RateLimiter(1, fetchDelay)

  init {
    logger.debug("({}) fetchDelay: {}", platform, fetchDelay)
    handleIntents()
    // Monitor app config changes
    scope.launch {
      app.appFlow.filterNotNull().collect { config ->
        // Update streamers config
        streamerStates.values.parallelStream().forEach { it.downloader?.updateAppConfig(config) }

        val globalConfig = platform.globalConfig(config)
        // Update rate limiter delay
        val newDelay = globalConfig.fetchDelay?.toDuration(DurationUnit.SECONDS)?.inWholeMilliseconds ?: 0L
        // do not update if the delay is the same
        if (newDelay == fetchDelay) {
          return@collect
        }
        rateLimiter.updateMinDelay(newDelay)
        fetchDelay = newDelay
        logger.debug("({}) updated fetchDelay: {}", platform, newDelay)
      }
    }

    scope.launch {
      downloadEngineConfigManager.streamConfigs(app.config.id).collect { newConfig ->
        logger.debug("({}) new engine config detected: {}", platform, newConfig)
        streamerStates.values
          .asSequence()
          .filter {
            // use downloader streamer here because it is the one that is actually used (configs populated)
            it.downloader?.streamer?.engineConfig?.javaClass == newConfig.javaClass
          }
          .filter { it.downloader != null }
          .forEach { it.downloader?.updateEngineConfig(newConfig) }
      }
    }
  }

  /**
   * Add streamer to download
   * @param streamer the streamer to download
   * @return true if streamer was added, false if already exists
   */
  suspend fun addStreamer(streamer: Streamer): Boolean = stateMutex.withLock {
    val currentState = streamerStates[streamer.url]
    if (currentState != null && currentState.state != CANCELLED) {
      logger.debug("({}) streamer {} already exists in state: {}", platform, streamer.url, currentState.state)
      return false
    }

    streamerStates[streamer.url] = StreamerState(streamer, PENDING)
    streamerChannel.send(streamer)
    logger.debug("({}) added to channel: {}", platform, streamer.url)
    true
  }

  /**
   * Cancel streamer download
   * @param streamer the streamer to cancel
   * @param reason reason for cancellation
   * @param newStreamer new streamer configuration if any
   */
  suspend fun cancelStreamer(streamer: Streamer, reason: String? = null, newStreamer: Streamer) = stateMutex.withLock {
    logger.debug(
      "({}) request to cancel: {} reason: {}, current state: {}",
      platform,
      streamer.url,
      reason,
      streamerStates[streamer.url]?.state
    )

    // Remove from pending if exists
    val currentState = streamerStates[streamer.url]
    if (currentState?.state == PENDING) {
      logger.debug("({}) removed pending streamer: {}", platform, streamer.url)
      sendCancellationEvent(currentState.streamer, "Download cancelled while pending: $reason")
      streamerStates.remove(streamer.url)
      return@withLock
    }

    // Get current downloader if exists
    val downloader = streamerStates[streamer.url]?.downloader
    if (downloader == null) {
      if (!streamerStates.containsKey(streamer.url)) {
        logger.debug("({}) streamer {} not found in any state", platform, streamer.url)
        return
      }
      // Remove from active streamers if not downloading
      streamerStates.remove(streamer.url)
    } else {
      // Cancel active download and mark state as cancelled
      logger.debug("({}), {} received cancellation signal : {}", platform, streamer.url, reason)
      streamerStates[streamer.url]?.state = CANCELLED
      downloader.cancelBlocking()

      // Wait for cleanup with timeout
      withTimeoutOrNull(10_000) {
        while (downloader.isDownloading) {
          delay(100)
        }
        streamerStates.remove(streamer.url)
      }
    }

    sendCancellationEvent(streamer, "Download cancelled: $reason")
  }

  /**
   * Handle intents
   */
  private fun handleIntents() {
    streamerChannel.receiveAsFlow()
      .buffer(Channel.BUFFERED)
      .onStart {
        logger.debug("({}) starting streamer flow", platform)
      }
      .onEach { streamer ->
        logger.debug("({}) processing streamer: {}", platform, streamer.url)
        // Move rate limiting outside of mutex lock to prevent blocking other operations
        val delay = rateLimiter.acquire()

        stateMutex.withLock {
          val state = streamerStates[streamer.url]
          if (state?.state != PENDING) {
            return@onEach
          }
          startDownloadJob(streamer)
          logger.debug(
            "({}) streamer {} added to download queue after delay: {}",
            platform, streamer.url, delay
          )
        }
      }
      .catch { e ->
        logger.error("Error in streamer flow", e)
      }
      .launchIn(scope)
  }


  private fun startDownloadJob(streamer: Streamer) {
    scope.launch(Dispatchers.Default + CoroutineName("${streamer.name}MainJob")) {
      logger.debug(
        "({}), {} launching download coroutine ({})",
        streamer.platform, streamer.url, this.coroutineContext
      )
      downloadStreamer(streamer)
    }
  }

  private suspend fun downloadStreamer(streamer: Streamer) {
    // Take state snapshot before starting download
    val state = stateMutex.withLock {
      val currentState = streamerStates[streamer.url] ?: return
      if (currentState.state != PENDING) {
        logger.debug(
          "({}) streamer {} is in invalid state: {}",
          platform, streamer.url, currentState.state
        )
        return
      }
      currentState.state = RESERVED
      logger.debug("({}) streamer {} reserved for download", platform, streamer.url)
      currentState
    }

    try {
      startPluginDownload(streamer)
    } finally {
      stateMutex.withLock {
        streamerStates.remove(streamer.url)
      }
    }
  }

  private suspend fun startPluginDownload(streamer: Streamer) {
    try {
      val newDownloadConfig = streamer.downloadConfig?.fillDownloadConfig(
        streamer.platform,
        streamer.templateStreamer?.downloadConfig,
        app.config
      )
      var isGlobalEngineConfig = false

      // Get engine from streamer or app config
      val streamerEngine: DownloadEngines = streamer.engine ?: DownloadEngines.fromString(app.config.engine)
      // fetch engine config

      val engineConfig = streamer.engine?.let { streamer.engineConfig }
        ?: downloadEngineConfigManager.getEngineConfig<EngineConfig>(
          app.config.id,
          streamerEngine.engine
        ).also { isGlobalEngineConfig = true }

      // Create new streamer with updated config
      val updatedStreamer =
        streamer.copy(downloadConfig = newDownloadConfig, engine = streamerEngine, engineConfig = engineConfig)

      var downloader: StreamerDownloadService
      stateMutex.withLock {
        val plugin = downloadFactory.createDownloader(app, updatedStreamer.platform, updatedStreamer.url)
        downloader = StreamerDownloadService(app, updatedStreamer, plugin, semaphore).apply {
          init(callback)
          listenToEngineChanges = isGlobalEngineConfig
        }
        val streamerState = streamerStates[streamer.url]
        if (streamerState?.state == RESERVED) {
          streamerState.downloader = downloader
          streamerState.state = DOWNLOADING
          logger.debug("({}) streamer {} starting download", platform, streamer.url)
        } else {
          logger.debug("({}) streamer {} is no longer reserved", platform, streamer.url)
          return
        }
      }
      downloader.start()
    } catch (e: Exception) {
      if (e is CancellationException) {
        logger.debug("({}) download cancelled for {}", platform, streamer.url)
      } else {
        logger.error("Failed to start download for ${streamer.name}:", e)
        EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))
      }
    }
  }

  /**
   * Cancel all downloads and cleanup resources
   */
  suspend fun cancel() = coroutineScope {
    stateMutex.withLock {
      logger.debug("({}) cancelling all downloads", platform)

      // Cancel all streamers in parallel
      streamerStates.values.map { state ->
        async {
          when (state.state) {
            PENDING -> {
              sendCancellationEvent(state.streamer, "Service shutdown while pending")
            }

            DOWNLOADING -> {
              state.downloader?.cancelBlocking()
              sendCancellationEvent(state.streamer, "Service shutdown")
            }

            else -> {} // No action needed
          }
        }
      }.awaitAll()

      // Clear all states
      streamerStates.clear()
      streamerChannel.close()
    }
  }

  /**
   * Helper method to send cancellation events
   * @param streamer the streamer being cancelled
   * @param reason reason for cancellation
   */
  private fun sendCancellationEvent(streamer: Streamer, reason: String) {
    EventCenter.sendEvent(
      StreamerRecordStop(
        streamer.name,
        streamer.url,
        streamer.platform,
        Clock.System.now(),
        reason = CancellationException(reason)
      )
    )
  }

}

