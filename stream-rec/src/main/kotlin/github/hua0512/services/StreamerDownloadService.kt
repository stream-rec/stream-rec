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
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.event.StreamerEvent.*
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamerState
import github.hua0512.download.exceptions.FatalDownloadErrorException
import github.hua0512.download.exceptions.InsufficientDownloadSizeException
import github.hua0512.download.exceptions.TimerEndedDownloadException
import github.hua0512.download.exceptions.UserStoppedDownloadException
import github.hua0512.flv.data.other.FlvMetadataInfo
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.download.base.OnStreamDownloaded
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.plugins.download.base.StreamerCallback
import github.hua0512.plugins.download.globalConfig
import github.hua0512.plugins.event.EventCenter
import github.hua0512.services.DownloadState.*
import github.hua0512.utils.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.slf4j.Logger
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private sealed class DownloadState {
  data object Idle : DownloadState()

  data object Preparing : DownloadState()

  data class CheckingDownload(val time: Long) : DownloadState()

  data class AwaitingDownload(val delay: Long) : DownloadState()

  data class Downloading(val duration: Long) : DownloadState()

  data class DownloadRetry(val count: Int, val error: Throwable? = null) : DownloadState()

  data object Cancelled : DownloadState()

  data object Finished : DownloadState()
}


/**
 * Class responsible for downloading streamer streams
 * @author hua0512
 * @date : 2024/4/21 20:15
 */
class StreamerDownloadService(
  private val app: App,
  internal var streamer: Streamer,
  private val plugin: PlatformDownloader<out DownloadConfig>,
  private val downloadSemaphore: Semaphore,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = logger(StreamerDownloadService::class.java)

    /**
     * Error threshold to check with the last error time, if the difference is less than this value
     * then it means the error is recent, and the download failed consecutively. In this case, delay the download
     * for a longer time.
     */
    private const val MIN_ERROR_THRESHOLD = 5000L

    /**
     * Maximum delay to wait before retrying the download if recent errors occurred
     */
    private const val MAX_ERROR_DELAY = 3600000L

    /**
     * Error threshold to check if the recent errors count is greater than this value
     */
    private const val ERROR_THRESHOLD = 3

  }

  private var isInitialized = false

  /**
   * List to store the downloaded stream data
   */
  private val dataList by lazy { mutableListOf<StreamData>() }

  // download retry count
  private var retryCount = 0

  // delay to wait before retrying the download, used when streams goes from live to offline
  private val retryDelay
    get() = app.config.downloadRetryDelay.toDuration(DurationUnit.SECONDS)

  // delay between download checks
  private val downloadInterval
    get() = (streamer.platform.globalConfig(app.config).downloadCheckInterval
      ?: app.config.downloadCheckInterval).toDuration(DurationUnit.SECONDS)

  // retry delay for parted downloads
  private val platformRetryDelay
    get() = (streamer.platform.globalConfig(app.config).partedDownloadRetry ?: 0).toDuration(DurationUnit.SECONDS)

  // max download retries
  private val maxRetry
    get() = app.config.maxDownloadRetries

  /**
   * Flag to check if the download is cancelled
   */
  private val isCancelled by lazy { MutableStateFlow(false) }

  /**
   * Flag to check if the download is in progress
   */
  var isDownloading = false

  /**
   * Flag to check if the download is in the timer range
   */
  private var inTimerRange = false

  /**
   * Timer job to stop the download after the timer ends
   */
  private var stopTimerJob: Job? = null


  /**
   * Timer job to await before downloading
   */
  private var awaitTimerJob: Job? = null

  /**
   * Callback to handle download events
   */
  private var callback: StreamerCallback? = null


  /**
   * Last error time
   */
  private var lastErrorTime: Long = 0

  /**
   * Recent errors count
   */
  private var recentErrors = 0

  /**
   * Recent error delay
   */
  private var currentErrorDelay = MIN_ERROR_THRESHOLD


  /**
   * Current download state
   */
  private val downloadState = MutableStateFlow<DownloadState>(Idle)


  suspend fun init(callback: StreamerCallback) {
    setCallback(callback)
    val initializationResult = plugin.init(
      streamer,
      this@StreamerDownloadService.callback,
      app.config.maxPartSize,
      app.config.maxPartDuration ?: 0
    )
    if (initializationResult.isErr) {
      val error = initializationResult.error
      logger.error("{} initialization error: {}", streamer.name, error)
      EventCenter.sendEvent(
        StreamerException(
          streamer.name,
          streamer.url,
          streamer.platform,
          Clock.System.now(),
          IllegalStateException("Initialization error: $error")
        )
      )
      isInitialized = false
      updateStreamerState(StreamerState.FATAL_ERROR)
      return
    }
    isInitialized = true
  }

  @OptIn(InternalCoroutinesApi::class)
  suspend fun start(): Unit {
    // create a supervisorScope with named coroutine
    // inherit parent coroutine
    val newContext = coroutineContext.newCoroutineContext(
      SupervisorJob() + CoroutineName("${streamer.name}MainScope")
    )

    val scope = CoroutineScope(newContext)

    if (!isInitialized) {
      clean()
      scope.cancel()
      return
    }

    // there might be a case of abnormal termination, so we reset the state to not live
    // reset the streamer state to not live
    updateStreamerState(StreamerState.NOT_LIVE)

    // prepare download
    downloadState to Preparing

    // job to handle download cancellation
    scope.launch {
      isCancelled.collect { cancelled ->
        if (cancelled) {
          val result = stop(UserStoppedDownloadException())
          logger.info("{} download stopped -> {}", streamer.name, result)
          if (result) {
            // cancel the timer jobs if it's active
            arrayOf(stopTimerJob, awaitTimerJob).forEach { job ->
              job?.let {
                if (it.isActive) it.cancel()
              }
            }
            downloadState to Cancelled
          }
        }
      }
    }

    downloadState.asStateFlow()
      .onEach {
        when (it) {
          Cancelled -> {
            logger.debug("{} download cancelled", streamer.name)
            // this is not needed since the cancel state is handled by the api
//            updateStreamerState(StreamerState.CANCELLED)
            clean()
            throw CancellationException("Download cancelled")
          }

          is DownloadRetry -> {
            val count = it.count
            // ignore errors at the moment
//            val error = it.error
            retryCount++ // increment retry count
            if (count >= maxRetry) {
              handleMaxRetry()
            } else {
              if (streamer.state == StreamerState.LIVE)
                updateStreamerState(StreamerState.INSPECTING_LIVE)
            }

            if (isCancelled.value) {
              downloadState to Cancelled
              return@onEach
            }
            downloadState to Preparing
          }

          is Downloading -> {
            val exception = handleLiveStreamer({ scope }, it.duration)
            if (isCancelled.value) {
              retryCount = maxRetry
              downloadState to DownloadRetry(retryCount)
              return@onEach
            }
            // retry after the download is finished
            downloadState to DownloadRetry(retryCount + 1, exception)
          }

          Finished -> {

          }

          Idle -> {

          }

          Preparing -> {
            val recordStartTime = streamer.startTime
//            val recordEndTime = streamer.endTime
            val delay = calculateDelay(recordStartTime ?: "")

            if (recentErrors >= ERROR_THRESHOLD) {
              logger.error(
                "{} too many errors, delaying download for {}",
                streamer.name,
                (currentErrorDelay).toDuration(DurationUnit.MILLISECONDS)
              )
              downloadState to AwaitingDownload(currentErrorDelay)
              currentErrorDelay = (currentErrorDelay * 2).coerceAtMost(MAX_ERROR_DELAY)
              return@onEach
            }

            downloadState to AwaitingDownload(delay)
          }

          is AwaitingDownload -> {
            val delay = it.delay
            // delay to wait before downloading
            if (delay > 0) {
              awaitTimerJob = scope.launch {
                logger.info("{} waiting for {}", streamer.name, delay.toDuration(DurationUnit.MILLISECONDS))
                delay(delay)
                downloadState to CheckingDownload(Clock.System.now().epochSeconds)
              }
              // wait for the timer job to finish
              awaitTimerJob!!.join()
              awaitTimerJob = null
            } else {
              downloadState to CheckingDownload(Clock.System.now().epochSeconds)
            }
          }

          is CheckingDownload -> {
            if (isCancelled.value) return@onEach
            inTimerRange = isInTimerRange(streamer.startTime ?: "", streamer.endTime ?: "").also { result ->
              if (!result) updateStreamerState(StreamerState.OUT_OF_SCHEDULE)
            }

            if (!inTimerRange || !isStreamerLive()) {
              handleOfflineStatus()
            } else {
              val duration = calculateDuration(streamer.endTime ?: "")
              downloadState to Downloading(duration)
              return@onEach
            }
            awaitTimerJob = scope.launch {
              delay(getDelay())
            }
            awaitTimerJob!!.join()
          }
        }
      }
      .collect {
        logger.trace("{} download state: {}", streamer.name, it)
      }
    clean()
    throw CancellationException("Download cancelled")
  }

  private suspend fun handleMaxRetry() {
    // reset retry count
    retryCount = 0
    // update state only if the streamer is not cancelled and its state is not live
    if (streamer.state != StreamerState.NOT_LIVE && !isCancelled.value) {
      updateStreamerState(StreamerState.NOT_LIVE)
    }
    // stream is not live or without data
    if (dataList.isEmpty()) {
      return
    }
    // stream finished with data
    logger.info("{} stream finished", streamer.name)
    EventCenter.sendEvent(
      StreamerOffline(
        streamer.name,
        streamer.url,
        streamer.platform,
        Clock.System.now(),
        dataList.toList()
      )
    )
    // update last live time
    updateLastLiveTime()
    // call onStreamingFinished callback with the copy of the list
    callback?.onStreamFinished(streamer.id, dataList.toList())
    dataList.clear()
    // reset recent errors
    resetRecentErrors()
    // fast exit if the download is cancelled
    if (isCancelled.value) return
    delay(downloadInterval)
  }

  /**
   * Check if streamer status is live
   * @return true if live stream is present, otherwise false
   * @throws CancellationException if downloader state mismatch
   */
  private suspend fun isStreamerLive(): Boolean {
    val result = plugin.shouldDownload()
    if (result.isOk) return result.value

    // result is error
    val state = when (result.error) {
      is ExtractorError.StreamerBanned, is ExtractorError.StreamerNotFound -> StreamerState.NOT_FOUND
      is ExtractorError.InitializationError, ExtractorError.InvalidExtractionUrl -> StreamerState.FATAL_ERROR
      else -> StreamerState.NOT_LIVE
    }
    updateStreamerState(state)
    if (state != StreamerState.NOT_LIVE) {
      cancelBlocking()
    }
    return false
  }

  private suspend fun handleLiveStreamer(scopeProvider: () -> CoroutineScope, duration: Long): Throwable? {
    if (duration > 0)
      scopeProvider().launchStopTask(duration)

    var shouldEnd = false
    var exception: Throwable? = null
    // while loop for parted download
    while (!isCancelled.value) {
      downloadStream(
        onStarted = {
          updateStreamerState(StreamerState.LIVE)
          updateLastLiveTime()
        }, onDownloaded = ::onSegmentDownloaded,
        onStreamDownloadError = {
          exception = it
          shouldEnd = true
          onSegmentFailed(it)
        }, onDownloadFinished = {
          shouldEnd = true
          retryCount = maxRetry
        })

      // break the loop if error occurred or download is cancelled
      if (shouldEnd || isCancelled.value) break

      delay(platformRetryDelay)
    }
    return exception
  }

  private suspend inline fun downloadStream(
    onStarted: () -> Unit,
    noinline onDownloaded: OnStreamDownloaded = { _, _ -> },
    onStreamDownloadError: (e: Throwable) -> Unit = {},
    crossinline onDownloadFinished: () -> Unit = {},
  ) {
    // streamer is live, start downloading
    var isStreamEnd: Boolean
    return downloadSemaphore.withPermit {
      isDownloading = true
      isStreamEnd = false
      onStarted()
      try {
        with(plugin) {
          onStreamDownloaded = onDownloaded
          onStreamFinished = {
            isStreamEnd = true
            onDownloadFinished()
          }
          download()
        }
        logger.debug("{} download finished", streamer.name)
      } catch (e: Exception) {
        EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))

        when (e) {

          is InsufficientDownloadSizeException -> {
            onStreamDownloadError(e)
            // update streamer state to inspecting live
            updateStreamerState(StreamerState.INSPECTING_LIVE)
            // hang the download until space is freed
            logger.error("{} insufficient download size, hanging...", streamer.name)
            while (true) {
              delay(30.toDuration(DurationUnit.SECONDS))
              if (isCancelled.value) {
                break
              }
              if (plugin.checkSpaceAvailable()) {
                logger.info("{} space available, resuming download", streamer.name)
                break
              }
            }
          }
          // in those cases, cancel the download and throw the exception
          is FatalDownloadErrorException, is CancellationException -> {
            updateStreamerState(StreamerState.FATAL_ERROR)
            logger.error("{} fatal exception", streamer.name, e)
            throw e
          }

          else -> {
            // do not log error if the stream has ended
            if (isStreamEnd) return
            onStreamDownloadError(e)
          }
        }
      } finally {
        isDownloading = false
      }
    }
  }


  private fun onSegmentDownloaded(data: StreamData, metaInfo: FlvMetadataInfo? = null) {
    dataList.add(data)
    callback?.onStreamDownloaded(streamer.id, data, metaInfo != null, metaInfo)
  }

  private fun onSegmentFailed(error: Throwable) {
    val nextRetry = retryCount + 1
    logger.error("{} unable to get stream data ({}/{}) due to: ", streamer.name, nextRetry, maxRetry, error)
    checkRecentErrors()
  }


  private suspend fun updateLastLiveTime() {
    val now = Clock.System.now()
    callback?.onLastLiveTimeChanged(streamer.id, now.epochSeconds) {
      streamer.lastLiveTime = now.epochSeconds
    }
  }


  private fun handleOfflineStatus() {
    // we empty the recent errors since the stream is offline
    resetRecentErrors()
    val nextRetry = retryCount + 1
    if (dataList.isNotEmpty()) {
      logger.error("{} is offline ({}/{})", streamer.name, nextRetry, maxRetry)
    } else {
      if (inTimerRange) logger.debug("{} is offline", streamer.name)
    }
    downloadState to DownloadRetry(nextRetry)
  }

  /**
   * Change the download state
   * @param state [DownloadState] new state
   * @receiver [MutableStateFlow] download state
   */
  private infix fun MutableStateFlow<DownloadState>.to(state: DownloadState) {
    value = state
  }

  /**
   * Returns the delay to wait before checking the stream again
   * if a data list is not empty, then it means the stream has ended
   * wait [retryDelay] seconds before checking again
   * otherwise wait [downloadInterval] seconds
   *
   * @return [Duration] delay
   */
  private fun getDelay(): Duration {
    return if (dataList.isNotEmpty()) {
      retryDelay
    } else {
      downloadInterval
    }
  }

  /**
   * Update the app config
   * @param config [AppConfig] new config
   */
  fun updateConfig(config: AppConfig) = plugin.onConfigUpdated(config)

  private suspend fun stop(exception: Exception? = null): Boolean = plugin.stopDownload(exception)

  fun cancel() {
    logger.debug("{} try cancel, isDownloading: {}", streamer.name, isDownloading)
    isCancelled.value = true
  }

  suspend fun cancelBlocking() {
    logger.debug("{} try blocking cancel, isDownloading: {}", streamer.name, isDownloading)
    isCancelled.emit(true)
  }


  private fun setCallback(callback: StreamerCallback) {
    this.callback = callback
  }

  private fun String.toJavaLocalDateTime(now: LocalDateTime? = null): LocalDateTime {
    val (hour, min, sec) = split(":").map { it.toInt() }
    val current = now ?: LocalDateTime.now()
    return current.withHour(hour).withMinute(min).withSecond(sec)
  }

  private fun isInTimerRange(definedStartTime: String, definedStopTime: String): Boolean {
    if (definedStartTime.isEmpty() || definedStopTime.isEmpty()) {
      return true
    }
    val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    val jStartTime = definedStartTime.toJavaLocalDateTime(currentTime)
    val jEndTime = definedStopTime.toJavaLocalDateTime(currentTime)
    return if (jStartTime.isAfter(jEndTime)) {
      currentTime.isAfter(jStartTime) || currentTime.isBefore(jEndTime)
    } else
      jStartTime.isBefore(currentTime) && jEndTime.isAfter(currentTime)
  }

  private fun calculateDelay(startTime: String): Long {
    if (startTime.isEmpty()) {
      return 0
    }
    val currentLocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    val jStartTime = startTime.toJavaLocalDateTime(currentLocalDateTime)
    val delay = java.time.Duration.between(currentLocalDateTime, jStartTime).toMillis().let {
      if (it < 0 || inTimerRange) 0 else it
    }
    return delay
  }


  private fun calculateDuration(endTime: String): Long {
    if (endTime.isEmpty()) {
      return 0
    }
    val currentLocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()

    // if the end time is 00:00:00, then the stream ends the next day
    val jEndTime = if (endTime == "00:00:00") {
      currentLocalDateTime.plusDays(1)
    } else {
      endTime.toJavaLocalDateTime(currentLocalDateTime)
    }
    // calculate the duration in milliseconds
    val duration = java.time.Duration.between(currentLocalDateTime, jEndTime).toMillis().let {
      // add 10 seconds to the duration to ensure the download is stopped
      if (it <= 60) it + 10000 else it
    }
    return duration
  }

  private fun CoroutineScope.launchStopTask(duration: Long) {
    logger.debug("{} stopping download after {} ms", streamer.name, duration)
    if (stopTimerJob?.isActive == true) {
      stopTimerJob!!.cancel()
    }
    stopTimerJob = launch {
      // add 10 seconds to the duration to ensure the download is stopped
      delay(if (duration <= 10000) 10000 else duration)
      inTimerRange = false
      if (isDownloading) {
        val result = stop(TimerEndedDownloadException())
        logger.info("{} timer end -> {}", streamer.name, result)
      }
    }
  }

  private suspend fun updateStreamerState(state: StreamerState) {
    // save streamer to the database with the new isLive value
    if (streamer.state != state) {
      if (state == StreamerState.LIVE) {
        EventCenter.sendEvent(
          StreamerOnline(
            streamer.name,
            streamer.url,
            streamer.platform,
            streamer.streamTitle ?: "",
            Clock.System.now()
          )
        )
      }
      callback?.onStateChanged(streamer.id, state) {
        streamer.state = state
      }
    }
  }

  private fun checkRecentErrors() {
    val now = Clock.System.now()

    // check if the error occurred recently
    if (abs(now.epochSeconds - lastErrorTime) < MIN_ERROR_THRESHOLD) {
      recentErrors++
    } else {
      // reset recent errors variables
      resetRecentErrors()
    }
    lastErrorTime = now.epochSeconds
  }

  private fun resetRecentErrors() {
    lastErrorTime = 0
    recentErrors = 0
    currentErrorDelay = MIN_ERROR_THRESHOLD
  }


  private fun clean() {
    awaitTimerJob?.cancel()
    stopTimerJob?.cancel()
    downloadState to Idle
    isDownloading = false
    dataList.clear()
    inTimerRange = false
    callback = null
    resetRecentErrors()
  }
}