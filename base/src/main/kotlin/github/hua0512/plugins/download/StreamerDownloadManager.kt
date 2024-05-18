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
import github.hua0512.data.config.Action
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.GlobalPlatformConfig
import github.hua0512.data.event.StreamerEvent.*
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.download.base.Download
import github.hua0512.plugins.download.exceptions.InvalidDownloadException
import github.hua0512.plugins.event.EventCenter
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
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
  private val streamer: Streamer,
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
  private val dataList = mutableListOf<StreamData>()

  /**
   * Returns the global platform config for the streamer platform
   */
  private val StreamingPlatform.platformConfig: GlobalPlatformConfig
    get() {
      return when (this) {
        StreamingPlatform.HUYA -> app.config.huyaConfig
        StreamingPlatform.DOUYIN -> app.config.douyinConfig
        StreamingPlatform.DOUYU -> app.config.douyuConfig
        StreamingPlatform.TWITCH -> app.config.twitchConfig
        StreamingPlatform.PANDALIVE -> app.config.pandaliveConfig
        else -> throw UnsupportedOperationException("Platform not supported")
      }
    }


  // download retry count
  private var retryCount = 0

  // delay to wait before retrying the download, used when streams goes from live to offline
  private val retryDelay = app.config.downloadRetryDelay.toDuration(DurationUnit.SECONDS)

  // delay between download checks
  private val downloadInterval = app.config.downloadCheckInterval.toDuration(DurationUnit.SECONDS)

  // retry delay for parted downloads
  private val platformRetryDelay =
    (streamer.platform.platformConfig.partedDownloadRetry ?: 0).toDuration(DurationUnit.SECONDS)

  // max download retries
  private val maxRetry = app.config.maxDownloadRetries

  /**
   * Flag to check if the download is cancelled
   */
  private var isCancelled = MutableStateFlow(false)

  /**
   * Flag to check if the download is in progress
   */
  private var isDownloading = false

  private var updateLiveStatusCallback: suspend (id: Long, isLive: Boolean) -> Unit = { _, _ -> }
  private var updateStreamerLastLiveTime: suspend (id: Long, lastLiveTime: Long) -> Unit = { _, _ -> }
  private var onSavedToDb: suspend (stream: StreamData) -> StreamData = { it }
  private var avatarUpdateCallback: (id: Long, avatarUrl: String) -> Unit = { _, _ -> }
  private var onDescriptionUpdateCallback: (id: Long, description: String) -> Unit = { _, _ -> }
  private var onRunningActions: suspend (data: List<StreamData>, actions: List<Action>) -> Unit = { _, _ -> }


  suspend fun init() = supervisorScope {
    plugin.apply {
      avatarUrlUpdateCallback {
        streamer.avatar = it
        logger.info("avatar updated : $it")
        avatarUpdateCallback(streamer.id, it)
      }
      descriptionUpdateCallback {
        streamer.streamTitle = it
        logger.info("description updated : $it")
        onDescriptionUpdateCallback(streamer.id, it)
      }
      init(streamer)
    }
  }

  private suspend fun handleMaxRetry(scope: CoroutineScope) {
    // reset retry count
    retryCount = 0
    // update db with the new isLive value
    if (streamer.isLive) updateLiveStatusCallback(streamer.id, false)
    streamer.isLive = false
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
    scope.launch {
      bindOnStreamingEndActions(streamer, dataList.toList())
    }
    dataList.clear()
    delay(downloadInterval)
  }

  private suspend fun checkStreamerLiveStatus(): Boolean {
    return try {
      // check if streamer is live
      plugin.shouldDownload()
    } catch (e: Exception) {
      when (e) {
        is IllegalArgumentException -> throw e // rethrow the exception
        else -> {
          logger.error("${streamer.name} error while checking if streamer is live : ${e.message}")
          false
        }
      }
    }
  }

  private suspend fun handleLiveStreamer(scope: CoroutineScope) {
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
      updateLiveStatusCallback(streamer.id, true)
    }
    streamer.isLive = true
    updateLastLiveTime()
    // while loop for parted download
    var breakLoop = false
    while (!breakLoop && !isCancelled.value) {
      downloadStream(onStreamDownloaded = { stream ->
        // save the stream data to the database
        scope.launch {
          val saved = saveStreamData(stream)
          // execute post parted download actions
          executePostActions(streamer, saved)
        }
      }) {
        logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
        breakLoop = true
      }
      if (breakLoop) break

      if (!isCancelled.value) delay(platformRetryDelay)
      else break
    }
  }

  private suspend fun downloadStream(onStreamDownloaded: (stream: StreamData) -> Unit = {}, onStreamDownloadError: (e: Exception) -> Unit = {}) {
    // streamer is live, start downloading
    // while loop for parting the download
    return downloadSemaphore.withPermit {
      isDownloading = true
      try {
        with(plugin) {
          onStreamDownloaded { onStreamDownloaded(it) }
          onStreamDownloadError { onStreamDownloadError(it) }
          download()
        }
        logger.debug("${streamer.name} download finished")
      } catch (e: Exception) {
        EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))
        when (e) {
          is IllegalArgumentException, is UnsupportedOperationException, is InvalidDownloadException -> {
            streamer.isLive = false
            updateLiveStatusCallback(streamer.id, false)
            logger.error("${streamer.name} invalid url or invalid engine : ${e.message}")
            throw e
          }

          is CancellationException -> {
            isCancelled.value = true
            throw e
          }

          else -> {
            logger.error("${streamer.name} Error while getting stream data : ${e.message}")
          }
        }
      } finally {
        isDownloading = false
      }
    }
  }


  private suspend fun updateLastLiveTime() {
    val now = Clock.System.now()
    updateStreamerLastLiveTime(streamer.id, now.epochSeconds)
    streamer.lastLiveTime = now.epochSeconds
  }

  private suspend fun saveStreamData(stream: StreamData): StreamData {
    var saved = stream
    try {
      saved = onSavedToDb(stream)
      logger.debug("saved to db : {}", saved)
    } catch (e: Exception) {
      logger.error("${streamer.name} error while saving $stream : ${e.message}")
    }
    dataList.add(saved)
    logger.info("${streamer.name} downloaded : $saved}")
    return saved
  }

  private suspend fun executePostActions(streamer: Streamer, streamData: StreamData) {
    try {
      executePostPartedDownloadActions(streamer, streamData)
    } catch (e: Exception) {
      logger.error("${streamer.name} error while executing post parted download actions : ${e.message}")
    }
  }

  private fun handleOfflineStreamer() {
    if (dataList.isNotEmpty()) {
      logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
    } else {
      logger.info("${streamer.name} is not live")
    }
  }

  suspend fun start(): Unit = supervisorScope {
    // download the stream
    launch {
      isCancelled.collect {
        if (it) {
          // await for the download to finish
          val result = stop()
          logger.info("${streamer.name} download stopped with result : $result")
          if (!isDownloading) {
            // break the loop if download is not in progress
            logger.info("${streamer.name} download canceled, not in progress")
            this@supervisorScope.cancel("Download cancelled")
          }
        }
      }
    }
    while (!isCancelled.value) {
      if (retryCount >= maxRetry) {
        handleMaxRetry(this)
        continue
      }
      val isLive = checkStreamerLiveStatus()

      if (isLive) {
        handleLiveStreamer(this)
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

  private suspend fun stop(): Boolean {
    isCancelled.value = true
    return plugin.stopDownload()
  }

  fun cancel() {
    logger.info("Cancelling download for ${streamer.name}, isDownloading : $isDownloading")
    isCancelled.value = true
  }


  private suspend fun bindOnStreamingEndActions(streamer: Streamer, streamDataList: List<StreamData>) {
    val actions =
      streamer.templateStreamer?.downloadConfig?.onStreamingFinished ?: streamer.downloadConfig?.onStreamingFinished
    actions?.let {
      onRunningActions(streamDataList, it)
    } ?: run {
      // check if on parted download is also empty
      val partedActions =
        streamer.templateStreamer?.downloadConfig?.onPartedDownload ?: streamer.downloadConfig?.onPartedDownload
      if (partedActions.isNullOrEmpty()) {
        // delete files if both onStreamFinished and onPartedDownload are empty
        if (app.config.deleteFilesAfterUpload) {
          streamDataList.forEach { Path(it.outputFilePath).deleteFile() }
        }
      }
    }
  }

  private suspend fun executePostPartedDownloadActions(streamer: Streamer, streamData: StreamData) {
    val actions =
      streamer.templateStreamer?.downloadConfig?.onPartedDownload ?: streamer.downloadConfig?.onPartedDownload
    actions?.let {
      onRunningActions(listOf(streamData), it)
    }
  }

  fun onRunningActions(onRunningActions: suspend (data: List<StreamData>, actions: List<Action>) -> Unit) {
    this.onRunningActions = onRunningActions
  }

  fun onAvatarUpdate(avatarUpdateCallback: (id: Long, avatarUrl: String) -> Unit) {
    this.avatarUpdateCallback = avatarUpdateCallback
  }

  fun onDescriptionUpdate(onDescriptionUpdateCallback: (id: Long, description: String) -> Unit) {
    this.onDescriptionUpdateCallback = onDescriptionUpdateCallback
  }

  fun onLiveStatusUpdate(updateStreamerLiveStatus: suspend (id: Long, isLive: Boolean) -> Unit) {
    this.updateLiveStatusCallback = updateStreamerLiveStatus
  }

  fun onLastLiveTimeUpdate(updateStreamerLastLiveTime: suspend (id: Long, lastLiveTime: Long) -> Unit) {
    this.updateStreamerLastLiveTime = updateStreamerLastLiveTime
  }

  fun onSavedToDb(onSavedToDb: suspend (stream: StreamData) -> StreamData) {
    this.onSavedToDb = onSavedToDb
  }

}