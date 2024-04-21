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

package github.hua0512.plugins.base

import github.hua0512.app.App
import github.hua0512.data.config.Action
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.GlobalPlatformConfig
import github.hua0512.data.event.StreamerEvent.*
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.event.EventCenter
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Class responsible for downloading streamer streams
 * @author hua0512
 * @date : 2024/4/21 20:15
 */
class StreamerDownload(
  override val coroutineContext: CoroutineContext,
  val app: App,
  val streamer: Streamer,
  val plugin: Download<DownloadConfig>,
  val downloadSemaphore: Semaphore,
) : CoroutineScope by CoroutineScope(coroutineContext + CoroutineName("Streamer-${streamer.name}") + Dispatchers.IO) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(StreamerDownload::class.java)
  }

  private val dataList = mutableListOf<StreamData>()

  private val StreamingPlatform.platformConfig: GlobalPlatformConfig
    get() {
      return when (this) {
        StreamingPlatform.HUYA -> app.config.huyaConfig
        StreamingPlatform.DOUYIN -> app.config.douyinConfig
        StreamingPlatform.DOUYU -> app.config.douyuConfig
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
  private val platformRetryDelay = (streamer.platform.platformConfig.partedDownloadRetry ?: 0).toDuration(DurationUnit.SECONDS)

  // max download retries
  private val maxRetry = app.config.maxDownloadRetries

  private var isCancelled = false


  private var updateLiveStatusCallback: suspend (id: Long, isLive: Boolean) -> Unit = { _, _ -> }
  private var updateStreamerLastLiveTime: suspend (id: Long, lastLiveTime: Long) -> Unit = { _, _ -> }
  private var checkShouldUpdateStreamerLastLiveTime: suspend (id: Long, lastLiveTime: Long, now: Long) -> Boolean = { _, _, _ -> false }
  private var onSavedToDb: suspend (stream: StreamData) -> Unit = {}
  private var avatarUpdateCallback: suspend (id: Long, avatarUrl: String) -> Unit = { _, _ -> }
  private var onDescriptionUpdateCallback: suspend (id: Long, description: String) -> Unit = { _, _ -> }
  private var onRunningActions: suspend (data: List<StreamData>, actions: List<Action>) -> Unit = { _, _ -> }


  suspend fun init() = coroutineScope {
    plugin.apply {
      init(streamer)
      avatarUrlUpdateCallback {
        streamer.avatar = it
        launch {
          avatarUpdateCallback(streamer.id, it)
        }
      }
      descriptionUpdateCallback {
        streamer.streamTitle = it
        launch {
          onDescriptionUpdateCallback(streamer.id, it)
        }
      }
    }
  }

  fun onRunningActions(onRunningActions: suspend (data: List<StreamData>, actions: List<Action>) -> Unit) {
    this.onRunningActions = onRunningActions
  }

  fun onAvatarUpdate(avatarUpdateCallback: suspend (id: Long, avatarUrl: String) -> Unit) {
    this.avatarUpdateCallback = avatarUpdateCallback
  }

  fun onDescriptionUpdate(onDescriptionUpdateCallback: suspend (id: Long, description: String) -> Unit) {
    this.onDescriptionUpdateCallback = onDescriptionUpdateCallback
  }

  fun onLiveStatusUpdate(updateStreamerLiveStatus: suspend (id: Long, isLive: Boolean) -> Unit) {
    this.updateLiveStatusCallback = updateStreamerLiveStatus
  }

  fun onLastLiveTimeUpdate(updateStreamerLastLiveTime: suspend (id: Long, lastLiveTime: Long) -> Unit) {
    this.updateStreamerLastLiveTime = updateStreamerLastLiveTime
  }

  fun onCheckLastLiveTime(checkShouldUpdateStreamerLastLiveTime: suspend (id: Long, lastLiveTime: Long, now: Long) -> Boolean) {
    this.checkShouldUpdateStreamerLastLiveTime = checkShouldUpdateStreamerLastLiveTime
  }

  fun onSavedToDb(onSavedToDb: suspend (stream: StreamData) -> Unit) {
    this.onSavedToDb = onSavedToDb
  }


  suspend fun launchDownload() = supervisorScope {
    // download the stream
    while (true) {
      if (retryCount >= maxRetry) {
        // reset retry count
        retryCount = 0
        // update db with the new isLive value
        if (streamer.isLive) updateLiveStatusCallback(streamer.id, false)
        streamer.isLive = false
        // stream is not live or without data
        if (dataList.isEmpty()) {
          continue
        }
        // stream finished with data
        logger.info("${streamer.name} stream finished")
        EventCenter.sendEvent(StreamerOffline(streamer.name, streamer.url, streamer.platform, Clock.System.now(), dataList.toList()))
        // call onStreamingFinished callback with the copy of the list
        launch {
          bindOnStreamingEndActions(streamer, dataList.toList())
        }
        dataList.clear()
        delay(downloadInterval)
        continue
      }
      val isLive = try {
        // check if streamer is live
        plugin.shouldDownload()
      } catch (e: Exception) {
        when (e) {
          is IllegalArgumentException -> {
            logger.error("${streamer.name} invalid url or invalid streamer : ${e.message}")
            return@supervisorScope
          }

          else -> {
            logger.error("${streamer.name} error while checking if streamer is live : ${e.message}")
            false
          }
        }
      }

      if (isLive) {
        // save streamer to the database with the new isLive value
        if (!streamer.isLive) {
          EventCenter.sendEvent(StreamerOnline(streamer.name, streamer.url, streamer.platform, streamer.streamTitle ?: "", Clock.System.now()))
          updateLiveStatusCallback(streamer.id, true)
        }
        streamer.isLive = true
        val now = Clock.System.now()
        // update last live time
        if (checkShouldUpdateStreamerLastLiveTime(streamer.id, streamer.lastLiveTime ?: 0, now.epochSeconds)) {
          updateStreamerLastLiveTime(streamer.id, now.epochSeconds)
          streamer.lastLiveTime = now.epochSeconds
        }
        // stream is live, start downloading
        // while loop for parting the download
        while (true) {
          val stream = downloadSemaphore.withPermit {
            try {
              plugin.download()
            } catch (e: Exception) {
              EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))
              when (e) {
                is IllegalArgumentException, is UnsupportedOperationException -> {
                  streamer.isLive = false
                  updateLiveStatusCallback(streamer.id, false)
                  logger.error("${streamer.name} invalid url or invalid engine : ${e.message}")
                  return@supervisorScope
                }

                else -> {
                  logger.error("${streamer.name} Error while getting stream data : ${e.message}")
                  null
                }
              }
            }
          }
          if (stream == null) {
            logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
            break
          }
          // save the stream data to the database
          try {
            onSavedToDb(stream)
            logger.debug("saved to db : {}", stream)
          } catch (e: Exception) {
            logger.error("${streamer.name} error while saving $stream : ${e.message}")
          }
          dataList.add(stream)
          logger.info("${streamer.name} downloaded : $stream}")
          launch { executePostPartedDownloadActions(streamer, stream) }
          // exit immediately if the download is cancelled
          if (isCancelled) {
            return@supervisorScope
          }
          delay(platformRetryDelay)
        }
      } else {
        if (dataList.isNotEmpty()) {
          logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
        } else {
          logger.info("${streamer.name} is not live")
        }
      }
      retryCount++
      /* if a data list is not empty, then it means the stream has ended
       * wait [retryDelay] seconds before checking again
       * otherwise wait [app.config.downloadCheckInterval] seconds
       */
      val duration = if (dataList.isNotEmpty()) {
        retryDelay
      } else {
        downloadInterval
      }
      delay(duration)
    }
  }


  suspend fun stopDownload() {
    plugin.stopDownload()
    isCancelled = true
  }

  private suspend fun bindOnStreamingEndActions(streamer: Streamer, streamDataList: List<StreamData>) {
    val actions = streamer.templateStreamer?.downloadConfig?.onStreamingFinished ?: streamer.downloadConfig?.onStreamingFinished
    actions?.let {
      onRunningActions(streamDataList, it)
    } ?: run {
      // check if on parted download is also empty
      val partedActions = streamer.templateStreamer?.downloadConfig?.onPartedDownload ?: streamer.downloadConfig?.onPartedDownload
      if (partedActions.isNullOrEmpty()) {
        // delete files if both onStreamFinished and onPartedDownload are empty
        if (app.config.deleteFilesAfterUpload) {
          streamDataList.forEach { Path(it.outputFilePath).deleteFile() }
        }
      }
    }
  }

  private suspend fun executePostPartedDownloadActions(streamer: Streamer, streamData: StreamData) {
    val actions = streamer.templateStreamer?.downloadConfig?.onPartedDownload ?: streamer.downloadConfig?.onPartedDownload
    actions?.let {
      onRunningActions(listOf(streamData), it)
    }
  }

}