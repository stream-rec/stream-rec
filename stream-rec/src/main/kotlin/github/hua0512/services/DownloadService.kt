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
import github.hua0512.data.dto.GlobalPlatformConfig
import github.hua0512.data.event.StreamerEvent.*
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.douyin.danmu.DouyinDanmu
import github.hua0512.plugins.douyin.download.Douyin
import github.hua0512.plugins.douyin.download.DouyinExtractor
import github.hua0512.plugins.douyu.danmu.DouyuDanmu
import github.hua0512.plugins.douyu.download.Douyu
import github.hua0512.plugins.douyu.download.DouyuExtractor
import github.hua0512.plugins.event.EventCenter
import github.hua0512.plugins.huya.danmu.HuyaDanmu
import github.hua0512.plugins.huya.download.Huya
import github.hua0512.plugins.huya.download.HuyaExtractor
import github.hua0512.repo.streamer.StreamDataRepo
import github.hua0512.repo.streamer.StreamerRepo
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DownloadService(
  private val app: App,
  private val actionService: ActionService,
  private val repo: StreamerRepo,
  private val streamDataRepository: StreamDataRepo,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(DownloadService::class.java)
  }

  // semaphore to limit the number of concurrent downloads
  private var downloadSemaphore: Semaphore? = null

  private fun getPlatformDownloader(platform: StreamingPlatform, url: String) = when (platform) {
    StreamingPlatform.HUYA -> Huya(app, HuyaDanmu(app), HuyaExtractor(app.client, app.json, url))
    StreamingPlatform.DOUYIN -> Douyin(app, DouyinDanmu(app), DouyinExtractor(app.client, app.json, url))
    StreamingPlatform.DOUYU -> Douyu(app, DouyuDanmu(app), DouyuExtractor(app.client, app.json, url))
    else -> throw Exception("Platform not supported")
  }

  private val StreamingPlatform.platformConfig: GlobalPlatformConfig
    get() {
      return when (this) {
        StreamingPlatform.HUYA -> app.config.huyaConfig
        StreamingPlatform.DOUYIN -> app.config.douyinConfig
        StreamingPlatform.DOUYU -> app.config.douyuConfig
        else -> throw UnsupportedOperationException("Platform not supported")
      }
    }

  private val taskJobs = mutableMapOf<Streamer, Job?>()

  suspend fun run() = coroutineScope {
    downloadSemaphore = Semaphore(app.config.maxConcurrentDownloads)
    // fetch all streamers from the database and start a job for each one
    repo.getStreamersActive().forEach { streamer ->
      startDownloadJob(streamer)
    }

    launch {
      repo.stream().distinctUntilChanged().buffer().collect { streamerList ->
        logger.info("Streamers changed, reloading...")

        // compare the new streamers with the old ones, first by url, then by entity equals
        // if a streamer is not in the new list, cancel the job
        // if a streamer is in the new list but not in the old one, start a new job
        // if a streamer is in both lists, do nothing

        if (streamerList.isEmpty()) {
          logger.info("No streamers to download")
          // the new list is empty, cancel all jobs
          taskJobs.values.forEach { it?.cancel() }
          return@collect
        }

        val oldStreamers = taskJobs.keys.map { it }

        val newStreamers = streamerList.filterNot { it.isTemplate }
        // cancel the jobs of the streamers that are not in the new list
        oldStreamers.filter { old ->
          newStreamers.none { new -> new.url == old.url }
        }.forEach { streamer ->
          cancelJob(streamer, "delete")
        }

        // diff the new streamers with the old ones
        // if a streamer has the same url but different entity, cancel the old job and start a new one
        // if a streamer is not in the old list, start a new job
        // if a streamer is in both lists, do nothing
        newStreamers.forEach { new ->
          val old = oldStreamers.find { it.url == new.url } ?: run {
            if (validateActivation(new)) return@forEach
            startDownloadJob(new)
            return@forEach
          }
          // if the entity is different, cancel the old job and start a new one
          // find the change reason
          if (old != new) {
            val reason = when {
              old.isActivated != new.isActivated -> "activation"
              old.url != new.url -> "url"
              old.downloadConfig != new.downloadConfig -> "download config"
              old.platform != new.platform -> "platform"
              old.name != new.name -> "name"
              old.isTemplate != new.isTemplate -> "as template"
              old.templateId != new.templateId -> "template id"
              old.templateStreamer?.downloadConfig != new.templateStreamer?.downloadConfig -> "template streamer download config"
              // other changes are ignored
              else -> return@forEach
            }
            logger.debug("Detected entity change for {}, {}", new, old)
            cancelJob(old, "entity changed : $reason")
            if (validateActivation(new)) return@forEach
            startDownloadJob(new)
          }
        }
      }
    }
  }


  private suspend fun downloadStreamer(streamer: Streamer) {
    val newJob = SupervisorJob(coroutineContext[Job])
    val newScope = CoroutineScope(coroutineContext + CoroutineName("Streamer-${streamer.name}") + newJob)
    newScope.launch {
      val plugin = try {
        getPlatformDownloader(streamer.platform, streamer.url)
      } catch (e: Exception) {
        logger.error("${streamer.name} platform not supported by the downloader : ${app.config.engine}")
        EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))
        return@launch
      }
      val streamDataList = mutableListOf<StreamData>()
      var retryCount = 0
      val retryDelay = app.config.downloadRetryDelay
      val maxRetry = app.config.maxDownloadRetries

      plugin.apply {
        init(streamer)
        // set update callbacks
        avatarUrlUpdateCallback {
          streamer.avatar = it
          newScope.launch {
            repo.updateStreamerAvatar(streamer.id, it)
          }
        }
        descriptionUpdateCallback {
          streamer.streamTitle = it
          newScope.launch {
            repo.updateStreamerStreamTitle(streamer.id, it)
          }
        }
      }
      while (true) {
        if (retryCount >= maxRetry) {
          retryCount = 0
          // update db with the new isLive value
          if (streamer.isLive) repo.updateStreamerLiveStatus(streamer.id, false)
          streamer.isLive = false
          // stream is not live or without data
          if (streamDataList.isEmpty()) {
            continue
          }
          // stream finished with data
          logger.info("${streamer.name} stream finished")
          EventCenter.sendEvent(StreamerOffline(streamer.name, streamer.url, streamer.platform, Clock.System.now(), streamDataList.toList()))
          // call onStreamingFinished callback with the copy of the list
          newScope.launch {
            bindOnStreamingEndActions(streamer, streamDataList.toList())
          }
          streamDataList.clear()
          delay(1.toDuration(DurationUnit.MINUTES))
          continue
        }
        val isLive = try {
          // check if streamer is live
          plugin.shouldDownload()
        } catch (e: Exception) {
          when (e) {
            is IllegalArgumentException -> {
              logger.error("${streamer.name} invalid url or invalid streamer : ${e.message}")
              return@launch
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
            repo.updateStreamerLiveStatus(streamer.id, true)
          }
          streamer.isLive = true
          val now = Clock.System.now()
          // update last live time
          if (repo.shouldUpdateStreamerLastLiveTime(streamer.id, streamer.lastLiveTime ?: 0, now.epochSeconds)) {
            repo.updateStreamerLastLiveTime(streamer.id, now.epochSeconds)
            streamer.lastLiveTime = now.epochSeconds
          }
          // stream is live, start downloading
          // while loop for parting the download
          while (true) {
            val stream = downloadSemaphore!!.withPermit {
              try {
                plugin.download()
              } catch (e: Exception) {
                EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))
                when (e) {
                  is IllegalArgumentException -> {
                    streamer.isLive = false
                    repo.updateStreamerLiveStatus(streamer.id, false)
                    logger.error("${streamer.name} invalid url or invalid streamer : ${e.message}")
                    return@launch
                  }

                  is UnsupportedOperationException -> {
                    streamer.isLive = false
                    repo.updateStreamerLiveStatus(streamer.id, false)
                    logger.error("${streamer.name} platform not supported by the downloader : ${app.config.engine}")
                    return@launch
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
              streamDataRepository.saveStreamData(stream)
              logger.debug("saved to db : {}", stream)
            } catch (e: Exception) {
              logger.error("${streamer.name} error while saving $stream : ${e.message}")
            }
            streamDataList.add(stream)
            logger.info("${streamer.name} downloaded : $stream}")
            newScope.launch { executePostPartedDownloadActions(streamer, stream) }
            val platformRetryDelay = streamer.platform.platformConfig.partedDownloadRetry ?: 0
            delay(platformRetryDelay.toDuration(DurationUnit.SECONDS))
          }
        } else {
          if (streamDataList.isNotEmpty()) {
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
        val duration = if (streamDataList.isNotEmpty()) {
          retryDelay.toDuration(DurationUnit.SECONDS)
        } else {
          app.config.downloadCheckInterval.toDuration(DurationUnit.SECONDS)
        }
        delay(duration)
      }
    }
  }

  private suspend fun bindOnStreamingEndActions(streamer: Streamer, streamDataList: List<StreamData>) {
    val actions = streamer.templateStreamer?.downloadConfig?.onStreamingFinished ?: streamer.downloadConfig?.onStreamingFinished
    actions?.let {
      actionService.runActions(streamDataList, it)
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
      actionService.runActions(listOf(streamData), it)
    }
  }


  /**
   * Starts a new download job for a given [Streamer].
   *
   * @param streamer The [Streamer] object for which to start the download job.
   */
  private fun CoroutineScope.startDownloadJob(streamer: Streamer) {
    val newJob = async { downloadStreamer(streamer) }
    taskJobs[streamer] = newJob
    logger.info("${streamer.name}, ${streamer.url} job started")
  }

  /**
   * Validates the activation status of a given [Streamer].
   *
   * @param new The [Streamer] object to validate.
   * @return true if the [Streamer] is not activated, false otherwise.
   */
  private fun validateActivation(new: Streamer): Boolean {
    if (!new.isActivated) {
      logger.info("${new.name}, ${new.url} is not activated")
      return true
    }
    return false
  }

  /**
   * Cancels the job of a given [Streamer].
   *
   * @param streamer The [Streamer] object for which to cancel the job.
   * @param reason The reason for cancelling the job.
   * @return The [Streamer] object that was cancelled.
   */
  private fun cancelJob(streamer: Streamer, reason: String = ""): Streamer {
    taskJobs[streamer]?.cancel(reason)?.also {
      logger.info("${streamer.name}, ${streamer.url} job cancelled : $reason")
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
    taskJobs.remove(streamer)
    return streamer
  }

  fun updateMaxConcurrentDownloads(max: Int) {
    downloadSemaphore = Semaphore(max)
  }
}