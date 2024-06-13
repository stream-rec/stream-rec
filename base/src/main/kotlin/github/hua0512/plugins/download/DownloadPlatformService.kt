/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
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

package github.hua0512.plugins.download

import github.hua0512.app.App
import github.hua0512.data.event.StreamerEvent.StreamerException
import github.hua0512.data.event.StreamerEvent.StreamerRecordStop
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.download.base.IPlatformDownloaderFactory
import github.hua0512.plugins.download.base.StreamerCallback
import github.hua0512.plugins.event.EventCenter
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap


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
  private val fetchDelay: Long = 0,
  private val semaphore: Semaphore,
  private val callback: StreamerCallback,
  private val platform: StreamingPlatform,
  private val downloadFactory: IPlatformDownloaderFactory,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(DownloadPlatformService::class.java)

    /**
     * Maximum number of streamers permitted by the platform
     * Used to limit the number of streamers to download
     */
    private const val MAX_STREAMERS = 500
  }


  private val streamers = mutableListOf<Streamer>()
  private val cancelledStreamers = ConcurrentSet<String>()
  private val downloadingStreamers = ConcurrentSet<String>()
  private val cancellationChannels = ConcurrentHashMap<String, Channel<String?>>()

  /**
   * Streamer channel with a capacity of MAX_STREAMERS
   *
   * This channel is used to add streamers to download
   * The reason for using a channel is to control the flow of streamers, due to the delay between adding streamers.
   * The platform fetchDelay is used to avoid launching multiple download jobs at the same time, or DDOSing the platform.
   *
   * @see MAX_STREAMERS
   */
  private val streamerChannel = Channel<Streamer>(MAX_STREAMERS)


  init {
    handleIntents()
  }

  /**
   * Add streamer to download
   * @param streamer the streamer to download
   */
  fun addStreamer(streamer: Streamer) {
    // check if streamer is in cancelled list, if so remove it
    if (streamer.url in cancelledStreamers) {
      cancelledStreamers.remove(streamer.url)
    }

    // push streamer to channel
    streamerChannel.trySend(streamer)
    logger.debug("({}) added to channel: {}", platform, streamer.url)
  }

  /**
   * Cancel streamer download
   * @param streamer the streamer to cancel
   */
  fun cancelStreamer(streamer: Streamer, reason: String? = null) {
    logger.debug("({}) request to cancel streamer: {} reason : {}", platform, streamer.url, reason)
    // check if streamer is present in the list
    if (!streamers.contains(streamer) &&
      !downloadingStreamers.contains(streamer.url) &&
      !cancelledStreamers.contains(streamer.url)
    ) {
      logger.debug("({}) streamer {} not found in the list", platform, streamer.url)
      return
    }

    cancelledStreamers.add(streamer.url)
    cancellationChannels[streamer.url]?.trySend(reason)
  }

  /**
   * Handle intents
   */
  private fun handleIntents() {
    // collect streamers
    streamerChannel.receiveAsFlow()
      .onEach {
        // check if streamer was cancelled before adding to state
        if (cancelledStreamers.contains(it.url)) {
          logger.debug("({}) streamer {} was cancelled before adding to state", it.platform, it.url)
          return@onEach
        }

        // check if streamer is already in the list
        // or is already being downloaded
        if (streamers.contains(it) || downloadingStreamers.contains(it.url)) {
          logger.debug("({}) streamer {} is already in the list", it.platform, it.url)
          return@onEach
        }

        logger.debug("({}) adding streamer: {}, {}", it.platform, it.name, it.url)
        // streamer cancellation channel
        val cancellationChannel = Channel<String?>(Channel.CONFLATED)

        streamers += it
        cancellationChannels[it.url] = cancellationChannel

        startDownloadJob(it)
        // delay before adding next streamer
        delay(fetchDelay)
      }.launchIn(scope)
  }


  private fun startDownloadJob(streamer: Streamer) {
    scope.launch {
      logger.debug("({}), {} launching download coroutine", streamer.platform, streamer.url)
      downloadStreamer(streamer)
    }
  }

  private suspend fun downloadStreamer(streamer: Streamer) = coroutineScope {
    // check if streamer is already being downloaded
    if (downloadingStreamers.contains(streamer.url)) {
      logger.debug("({}) streamer {} is already being downloaded", platform, streamer.url)
      return@coroutineScope
    }

    logger.debug("({}) downloading streamer: {}, {}", platform, streamer.name, streamer.url)

    downloadingStreamers.add(streamer.url)
    val cancellationChannel = cancellationChannels[streamer.url]

    try {
      // download streamer job
      var manager: StreamerDownloadManager? = null
      val downloadJob = async {
        downloadStreamerInternal(streamer) {
          manager = it
        }
      }
      val reason = cancellationChannel?.receive()
      logger.debug("({}), streamer {} received cancellation signal : {}", platform, streamer.url, reason)
      // received cancellation signal
      // handle cancellation
      manager?.cancel()
      downloadJob.join()

      EventCenter.sendEvent(
        StreamerRecordStop(
          streamer.name,
          streamer.url,
          streamer.platform,
          Clock.System.now(),
          reason = CancellationException("Download cancelled : $reason")
        )
      )
    } finally {
      streamers.remove(streamer)
      cancellationChannels.remove(streamer.url)
      downloadingStreamers.remove(streamer.url)
    }
  }

  private suspend fun downloadStreamerInternal(streamer: Streamer, onInit: (StreamerDownloadManager) -> Unit = {}) {
    val plugin = try {
      downloadFactory.createDownloader(app, streamer.platform, streamer.url)
    } catch (e: Exception) {
      logger.error("${streamer.name} platform not supported by the downloader : ${app.config.engine}, $e")
      EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))
      return
    }
    val streamerDownload = StreamerDownloadManager(
      app,
      streamer,
      plugin,
      semaphore
    ).apply {
      init(callback)
    }
    onInit(streamerDownload)
    streamerDownload.start()
  }

  fun cancel() {
    // send cancellation signal to all streamers
    cancellationChannels.forEach { (_, channel) ->
      channel.trySend("App shutdown")
    }
  }

}