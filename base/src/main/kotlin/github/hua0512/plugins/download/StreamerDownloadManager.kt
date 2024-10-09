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
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.GlobalPlatformConfig
import github.hua0512.data.event.StreamerEvent.*
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.download.exceptions.FatalDownloadErrorException
import github.hua0512.download.exceptions.TimerEndedDownloadException
import github.hua0512.download.exceptions.UserStoppedDownloadException
import github.hua0512.plugins.download.base.Download
import github.hua0512.plugins.download.base.OnStreamDownloaded
import github.hua0512.plugins.download.base.StreamerCallback
import github.hua0512.plugins.event.EventCenter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Class responsible for downloading streamer streams
 * @author hua0512
 * @date : 2024/4/21 20:15
 */
class StreamerDownloadManager(
  private val app: App,
  private var streamer: Streamer,
  private val plugin: Download<DownloadConfig>,
  private val downloadSemaphore: Semaphore,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(StreamerDownloadManager::class.java)
  }

  /**
   * List to store the downloaded stream data
   */
  private val dataList by lazy { mutableListOf<StreamData>() }

  // download retry count
  private var retryCount = 0

  // delay to wait before retrying the download, used when streams goes from live to offline
  private val retryDelay = app.config.downloadRetryDelay.toDuration(DurationUnit.SECONDS)

  // delay between download checks
  private val downloadInterval = app.config.downloadCheckInterval.toDuration(DurationUnit.SECONDS)

  // retry delay for parted downloads
  private val platformRetryDelay =
    (streamer.platform.platformConfig(app.config).partedDownloadRetry ?: 0).toDuration(DurationUnit.SECONDS)

  // max download retries
  private val maxRetry = app.config.maxDownloadRetries

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
  private var inTimerRange = true

  /**
   * Timer job to stop the download after the timer ends
   */
  private var stopTimerJob: Job? = null

  /**
   * Callback to handle download events
   */
  private var callback: StreamerCallback? = null


  suspend fun init(callback: StreamerCallback) {
    setCallback(callback)
    plugin.apply {
      this.callback = this@StreamerDownloadManager.callback
      init(streamer)
    }
  }

  private suspend fun handleMaxRetry() {
    // reset retry count
    retryCount = 0
    // update db with the new isLive value
    resetStreamerLiveStatus()
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
    callback?.onStreamFinished(streamer, dataList.toList())
    dataList.clear()
    delay(downloadInterval)
  }

  private suspend fun checkStreamerLiveStatus(): Boolean {
    // check if streamer is live
    // only InvalidExtractionUrlException is thrown if the url is invalid
    return plugin.shouldDownload()
  }

  private suspend fun handleLiveStreamer() {
    // save streamer to the database with the new isLive value
    if (!streamer.isLive) {
      EventCenter.sendEvent(
        StreamerOnline(
          streamer.name,
          streamer.url,
          streamer.platform,
          streamer.streamTitle ?: "",
          Clock.System.now()
        )
      )
      streamer.isLive = true
      callback?.onLiveStatusChanged(streamer, true)
    }
    updateLastLiveTime()
    var hasError = false
    // while loop for parted download
    while (!isCancelled.value && inTimerRange) {
      downloadStream(onDownloaded = { stream, metaInfo ->
        callback?.onStreamDownloaded(streamer, stream, metaInfo != null, metaInfo)
        dataList.add(stream)
      }) {
        logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
        hasError = true
      }
      // break the loop if error occurred or download is cancelled
      if (hasError || isCancelled.value) break

      delay(platformRetryDelay)
    }
  }

  private suspend inline fun downloadStream(
    crossinline onDownloaded: OnStreamDownloaded = { _, _ -> },
    crossinline onStreamDownloadError: (e: Throwable) -> Unit = {},
  ) {
    // streamer is live, start downloading
    // while loop for parting the download
    return downloadSemaphore.withPermit {
      isDownloading = true
      try {
        with(plugin) {
          onStreamDownloaded { stream, metaInfo ->
            onDownloaded(stream, metaInfo)
          }
          download()
        }
        logger.debug("${streamer.name} download finished")
      } catch (e: Exception) {
        EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))

        when (e) {
          // in those cases, cancel the download and throw the exception
          is FatalDownloadErrorException, is CancellationException -> {
            streamer.isLive = false
            callback?.onLiveStatusChanged(streamer, false)
            if (e is FatalDownloadErrorException)
              logger.error("${streamer.name} fatal exception", e)
            throw e
          }

          else -> {
            logger.error("${streamer.name} download error", e)
            onStreamDownloadError(e)
          }
        }
      } finally {
        isDownloading = false
      }
    }
  }


  private fun updateLastLiveTime() {
    val now = Clock.System.now()
    callback?.onLastLiveTimeChanged(streamer, now.epochSeconds)
    streamer.lastLiveTime = now.epochSeconds
  }

  private fun handleOfflineStreamer() {
    if (dataList.isNotEmpty()) {
      logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
    } else {
      logger.info("${streamer.name} is not live")
    }
    // there might be a case when the user exited the app while the stream is live
    // we reset the isLive value to false to avoid any issues
    resetStreamerLiveStatus()
  }

  suspend fun start(): Unit = supervisorScope {
    launch {
      isCancelled.collect {
        if (it) {
          val result = stop(UserStoppedDownloadException())
          logger.info("${streamer.name} download stopped: $result")
          // cancel the timer job if it's active
          if (stopTimerJob?.isActive == true) {
            stopTimerJob?.cancel()
          }
          stopTimerJob = null
          this@supervisorScope.cancel("Download cancelled")
        }
      }
    }

    while (!isCancelled.value) {
      if (retryCount >= maxRetry) {
        handleMaxRetry()
        continue
      }

      val recordStartTime = streamer.startTime
      val recordEndTime = streamer.endTime
      if (recordStartTime != null && recordEndTime != null) {
        if (recordStartTime == recordEndTime) {
          throw CancellationException("${streamer.name} SAME_START_END_TIME")
        }
        handleTimerDuration(recordStartTime, recordEndTime)
      }

      val isLive = checkStreamerLiveStatus()

      if (isLive) {
        handleLiveStreamer()
      } else {
        handleOfflineStreamer()
      }
      if (isCancelled.value) break
      retryCount++
      delay(getDelay())
    }
    throw CancellationException("Download cancelled")
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

  suspend fun cancelBlocking(newStreamer: Streamer) {
    logger.info("${streamer.name} try cancel, isDownloading: {}", isDownloading)
    updateStreamer(newStreamer)
    isCancelled.emit(true)
  }


  fun setCallback(callback: StreamerCallback) {
    this.callback = callback
  }

  fun updateStreamer(newStreamer: Streamer) {
    this.plugin.updateStreamer(newStreamer)
    this.streamer = newStreamer
  }


  private suspend fun CoroutineScope.handleTimerDuration(definedStartTime: String, definedStopTime: String) {

    fun CoroutineScope.launchStopTask(duration: Long) {
      logger.debug("(${streamer.name}) stopping download after $duration ms")
      if (stopTimerJob?.isActive == true) {
        stopTimerJob?.cancel()
      }
      stopTimerJob = launch {
        delay(duration)
        inTimerRange = false
        if (isDownloading) {
          val result = stop(TimerEndedDownloadException())
          logger.info("(${streamer.name}) timer task stop triggered: $result")
          resetStreamerLiveStatus()
        }
      }
    }

    val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    val (startHour, startMin, startSec) = definedStartTime.split(":").map { it.toInt() }
    val (endHour, endMin, endSec) = definedStopTime.split(":").map { it.toInt() }
    var jStartTime = currentTime.withHour(startHour).withMinute(startMin).withSecond(startSec)
    var jEndTime = jStartTime.withHour(endHour).withMinute(endMin).withSecond(endSec).let {
      if (endHour < startHour) it.plusDays(1) else it
    }

    // delayMillis is the time to wait before starting the download
    // durationMillis is the time to wait before stopping the download
    val (delayMillis, durationMillis) = when {
      currentTime.isBefore(jStartTime) -> {
        val delay = java.time.Duration.between(currentTime, jStartTime).toMillis()
        val duration = java.time.Duration.between(jStartTime, jEndTime).toMillis()
        logger.info("${streamer.name} before start time, waiting for $delay ms")
        delay to duration
      }

      currentTime.isAfter(jEndTime) -> {
        // plus one day to get the next start time
        jStartTime = jStartTime.plusDays(1)
        jEndTime = jEndTime.plusDays(1)
        val delay = java.time.Duration.between(currentTime, jStartTime).toMillis()
        val duration = java.time.Duration.between(jStartTime, jEndTime).toMillis()
        logger.info("${streamer.name} end time passed, waiting for $delay ms")
        delay to duration
      }

      currentTime.isAfter(jStartTime) && currentTime.isBefore(jEndTime) -> {
        val duration = java.time.Duration.between(currentTime, jEndTime).toMillis()
        0L to duration
      }

      else -> {
        // should not reach here
        inTimerRange = false
        logger.error("${streamer.name} outside timer range")
        throw CancellationException("${streamer.name} outside timer range")
        return
      }
    }
    // ensure the streamer is not live
    resetStreamerLiveStatus()
    delay(delayMillis)
    inTimerRange = true
    launchStopTask(durationMillis)
  }

  private fun resetStreamerLiveStatus() {
    if (streamer.isLive) {
      streamer.isLive = false
      callback?.onLiveStatusChanged(streamer, false)
    }
  }
}


/**
 * Returns the global platform config for the platform
 *
 * @param config [AppConfig] global app config
 * @return [GlobalPlatformConfig] streaming global platform config
 */
fun StreamingPlatform.platformConfig(config: AppConfig): GlobalPlatformConfig = when (this) {
  StreamingPlatform.HUYA -> config.huyaConfig
  StreamingPlatform.DOUYIN -> config.douyinConfig
  StreamingPlatform.DOUYU -> config.douyuConfig
  StreamingPlatform.TWITCH -> config.twitchConfig
  StreamingPlatform.PANDATV -> config.pandaTvConfig
  else -> throw UnsupportedOperationException("Platform not supported")
}