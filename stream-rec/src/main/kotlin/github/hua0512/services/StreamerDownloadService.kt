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

package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.event.StreamerEvent.*
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamerState
import github.hua0512.download.exceptions.FatalDownloadErrorException
import github.hua0512.download.exceptions.TimerEndedDownloadException
import github.hua0512.download.exceptions.UserStoppedDownloadException
import github.hua0512.plugins.base.exceptions.InvalidExtractionInitializationException
import github.hua0512.plugins.base.exceptions.InvalidExtractionUrlException
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
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private sealed class DownloadState {
  data object Idle : DownloadState()

  data object Preparing : DownloadState()

  data class CheckingDownload(val time: Long) : DownloadState()

  data class AwaitingDownload(val delay: Long, val duration: Long) : DownloadState()

  data class Downloading(val duration: Long) : DownloadState()

  data class DownloadRetry(val count: Int, val error: Exception? = null) : DownloadState()

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
  private var streamer: Streamer,
  private val plugin: PlatformDownloader<out DownloadConfig>,
  private val downloadSemaphore: Semaphore,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = logger(StreamerDownloadService::class.java)
  }

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
    get() = app.config.downloadCheckInterval.toDuration(DurationUnit.SECONDS)

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
  private var isDownloading = false

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


  private val downloadState = MutableStateFlow<DownloadState>(Idle)


  suspend fun init(callback: StreamerCallback) {
    setCallback(callback)
    plugin.init(
      streamer,
      this@StreamerDownloadService.callback,
      app.config.maxPartSize,
      app.config.maxPartDuration ?: 0
    )
  }

  private suspend fun handleMaxRetry() {
    // reset retry count
    retryCount = 0
    if (streamer.state != StreamerState.NOT_LIVE && !isCancelled.value) {
      updateStreamerState(StreamerState.NOT_LIVE)
    }
    // stream is not live or without data
    if (dataList.isEmpty()) {
      return
    }
    // stream finished with data
    logger.info("${streamer.name} stream finished")
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
    if (isCancelled.value) return
    delay(downloadInterval)
  }

  /**
   * Check if streamer status is live
   * @return true if live stream is present, otherwise false
   * @throws CancellationException if downloader state mismatch
   * @throws InvalidExtractionUrlException if the streamer url is not supported by this extractor
   * @throws InvalidExtractionInitializationException if the initialization of the extractor failed
   */
  private suspend fun checkStreamerLiveStatus(): Boolean = try {
    plugin.shouldDownload()
  } catch (e: Exception) {
    // cancel streamer scope by throwing CancellationException
    throw CancellationException(e.message)
  }

  private suspend fun CoroutineScope.handleLiveStreamer(duration: Long) {
    if (duration > 0)
      launchStopTask(duration)

    var shouldEnd = false
    // while loop for parted download
    while (!isCancelled.value) {
      downloadStream(onStarted = {
        updateStreamerState(StreamerState.LIVE)
        updateLastLiveTime()
      }, onDownloaded = { stream, metaInfo ->
        callback?.onStreamDownloaded(streamer.id, stream, metaInfo != null, metaInfo)
        dataList.add(stream)
      }, onStreamDownloadError = {
        logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
        shouldEnd = true
      }, onDownloadFinished = {
        shouldEnd = true
        retryCount = 3
      })

      // break the loop if error occurred or download is cancelled
      if (shouldEnd || isCancelled.value) break

      delay(platformRetryDelay)
    }
  }

  private suspend inline fun downloadStream(
    onStarted: () -> Unit,
    noinline onDownloaded: OnStreamDownloaded = { _, _ -> },
    crossinline onStreamDownloadError: (e: Throwable) -> Unit = {},
    noinline onDownloadFinished: () -> Unit = {},
  ) {
    // streamer is live, start downloading
    var isStreamEnd = false
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
        logger.debug("${streamer.name} download finished")
      } catch (e: Exception) {
        EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))

        when (e) {
          // in those cases, cancel the download and throw the exception
          is FatalDownloadErrorException, is CancellationException -> {
            updateStreamerState(StreamerState.FATAL_ERROR)
            if (e !is CancellationException)
              logger.error("${streamer.name} fatal exception", e)
            throw e
          }

          else -> {
            logger.error("${streamer.name} download error", e)
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


  private suspend fun updateLastLiveTime() {
    val now = Clock.System.now()
    callback?.onLastLiveTimeChanged(streamer.id, now.epochSeconds) {
      streamer.lastLiveTime = now.epochSeconds
    }
  }

  private fun handleOfflineStreamer() {
    if (dataList.isNotEmpty()) {
      logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
    } else {
      if (inTimerRange) logger.info("${streamer.name} is not live")
    }
    downloadState changeTo DownloadRetry(retryCount + 1)
  }

  suspend fun start(): Unit = sScope@ supervisorScope {
    // there might be a case when the user exited the app while the stream is live
    // reset the streamer state to not live
    updateStreamerState(StreamerState.NOT_LIVE)

    // prepare download
    downloadState changeTo Preparing

    launch {
      isCancelled.collect {
        if (it) {
          val result = stop(UserStoppedDownloadException())
          logger.info("${streamer.name} download stopped: $result")
          if (result) {
            // cancel the timer job if it's active
            stopTimerJob?.let {
              if (it.isActive) it.cancel()
            }
            awaitTimerJob?.let {
              if (it.isActive) it.cancel()
            }
            downloadState changeTo Cancelled
          }
        }
      }
    }

    downloadState.asStateFlow()
      .onEach {
        when (it) {
          Cancelled -> {
            logger.debug("({}) download cancelled", streamer.name)
            updateStreamerState(StreamerState.CANCELLED)
            clean()
            throw CancellationException("Download cancelled")
          }

          is DownloadRetry -> {
            val count = it.count
            val error = it.error
            retryCount++ // increment retry count
            if (count >= maxRetry) {
              handleMaxRetry()
            } else {
              if (streamer.state == StreamerState.LIVE)
                updateStreamerState(StreamerState.INSPECTING_LIVE)
            }

            if (isCancelled.value) {
              downloadState changeTo Cancelled
              return@onEach
            }
            downloadState changeTo Preparing
          }

          is Downloading -> {
            handleLiveStreamer(it.duration)
            if (isCancelled.value) {
              retryCount = 3
              downloadState changeTo DownloadRetry(retryCount)
              return@onEach
            }
            // retry after the download is finished
            downloadState changeTo DownloadRetry(retryCount + 1)
          }

          Finished -> {

          }

          Idle -> {

          }

          Preparing -> {
            val recordStartTime = streamer.startTime
            val recordEndTime = streamer.endTime
            var delay = calculateDelay(recordStartTime ?: "")
            var duration = 0L
            downloadState changeTo AwaitingDownload(delay, duration = duration)
          }

          is AwaitingDownload -> {
            val delay = it.delay
            val duration = it.duration
            // delay to wait before downloading
            awaitTimerJob = launch {
              delay(delay)
              downloadState changeTo CheckingDownload(Clock.System.now().epochSeconds)
            }
            // wait for the timer job to finish
            awaitTimerJob!!.join()
            awaitTimerJob = null
          }

          is CheckingDownload -> {
            if (isCancelled.value) return@onEach
            inTimerRange = isInTimerRange(streamer.startTime ?: "", streamer.endTime ?: "").also {
              if (!it) updateStreamerState(StreamerState.OUT_OF_SCHEDULE)
            }

            if (!inTimerRange || !checkStreamerLiveStatus()) {
              handleOfflineStreamer()
            } else {
              val duration = calculateDuration(streamer.endTime ?: "")
              downloadState changeTo Downloading(duration)
              return@onEach
            }
            awaitTimerJob = launch {
              delay(getDelay())
            }
            awaitTimerJob!!.join()
          }
        }
      }
      .collect {
        logger.debug("({}) download state: {}", streamer.name, it)
      }
    clean()
    throw CancellationException("Download cancelled")
  }

  private infix fun MutableStateFlow<DownloadState>.changeTo(state: DownloadState) {
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

  private suspend fun stop(exception: Exception? = null): Boolean = plugin.stopDownload(exception)

  fun cancel() {
    logger.info("${streamer.name} try cancel, isDownloading: {}", isDownloading)
    isCancelled.value = true
  }

  suspend fun cancelBlocking() {
    logger.info("${streamer.name} try cancel, isDownloading: {}", isDownloading)
    isCancelled.emit(true)
  }


  fun setCallback(callback: StreamerCallback) {
    this.callback = callback
  }

  private fun String.toJavaLocalDateTime(now: LocalDateTime? = null): java.time.LocalDateTime {
    val (hour, min, sec) = split(":").map { it.toInt() }
    val current = now ?: java.time.LocalDateTime.now()
    return current.withHour(hour).withMinute(min).withSecond(sec)
  }

  private fun isInTimerRange(definedStartTime: String, definedStopTime: String): Boolean {
    if (definedStartTime.isEmpty() || definedStopTime.isEmpty()) {
      return true
    }
    val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    var jStartTime = definedStartTime.toJavaLocalDateTime(currentTime)
    var jEndTime = definedStopTime.toJavaLocalDateTime(currentTime)
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
    logger.debug("(${streamer.name}) stopping download after $duration ms")
    if (stopTimerJob?.isActive == true) {
      stopTimerJob!!.cancel()
    }
    stopTimerJob = launch {
      // add 10 seconds to the duration to ensure the download is stopped
      delay(if (duration <= 10000) 10000 else duration)
      inTimerRange = false
      if (isDownloading) {
        val result = stop(TimerEndedDownloadException())
        logger.info("(${streamer.name}) timer task stop triggered: $result")
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

  fun clean() {
    downloadState changeTo Idle
    isDownloading = false
    dataList.clear()
    inTimerRange = false
    awaitTimerJob?.cancel()
    stopTimerJob?.cancel()
    callback = null
  }
}