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
import github.hua0512.data.event.StreamerEvent.StreamerException
import github.hua0512.data.event.StreamerEvent.StreamerRecordStop
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.base.StreamerDownload
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

  private val taskJobs = mutableMapOf<Streamer, Job?>()
  private val plugins = mutableMapOf<Streamer, StreamerDownload>()

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
    val plugin = try {
      getPlatformDownloader(streamer.platform, streamer.url)
    } catch (e: Exception) {
      logger.error("${streamer.name} platform not supported by the downloader : ${app.config.engine}")
      EventCenter.sendEvent(StreamerException(streamer.name, streamer.url, streamer.platform, Clock.System.now(), e))
      return
    }

    val streamerDownload = StreamerDownload(
      currentCoroutineContext(),
      app,
      streamer,
      plugin,
      downloadSemaphore!!
    ).apply {
      init()

      onLiveStatusUpdate { id, isLive ->
        repo.updateStreamerLiveStatus(id, isLive)
      }

      onLastLiveTimeUpdate { id, lastLiveTime ->
        repo.updateStreamerLastLiveTime(id, lastLiveTime)
      }

      onCheckLastLiveTime { id, lastLiveTime, now ->
        repo.shouldUpdateStreamerLastLiveTime(id, lastLiveTime, now)
      }
      onSavedToDb {
        streamDataRepository.saveStreamData(it)
      }

      onDescriptionUpdate { id, description ->
        repo.updateStreamerStreamTitle(id, description)
      }

      onAvatarUpdate { id, avatarUrl ->
        repo.updateStreamerAvatar(id, avatarUrl)
      }

      onRunningActions { data, actions ->
        actionService.runActions(data, actions)
      }
    }
    plugins[streamer] = streamerDownload
    streamerDownload.launchDownload()
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
  private suspend fun cancelJob(streamer: Streamer, reason: String = ""): Streamer {
    // stop the download
    plugins[streamer]?.stopDownload()
    // await the job to finish
    taskJobs[streamer]?.join()
    // cancel the job
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
    plugins.remove(streamer)
    return streamer
  }

  fun updateMaxConcurrentDownloads(max: Int) {
    downloadSemaphore = Semaphore(max)
  }
}