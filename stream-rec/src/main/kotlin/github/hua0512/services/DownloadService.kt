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
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.download.DownloadPlatformService
import github.hua0512.plugins.download.base.StreamerCallback
import github.hua0512.plugins.download.platformConfig
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.utils.deleteFile
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
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
  private lateinit var downloadSemaphore: Semaphore

  // map of platform to download service
  private val taskJobs = ConcurrentHashMap<StreamingPlatform, DownloadPlatformService>()

  private lateinit var callback: StreamerCallback

  private var streamers = emptyList<Streamer>()

  private lateinit var scope: CoroutineScope

  /**
   * Starts the download service.
   */
  suspend fun run(downloadScope: CoroutineScope) {
    downloadSemaphore = Semaphore(app.config.maxConcurrentDownloads)
    this.scope = downloadScope
    callback = object : StreamerCallback {
      override fun onLiveStatusChanged(streamer: Streamer, isLive: Boolean) {
        scope.launch {
          logger.debug("({}) live status changed to {}", streamer.name, isLive)
          repo.update(streamer.copy(isLive = isLive))
        }
      }

      override fun onLastLiveTimeChanged(streamer: Streamer, lastLiveTime: Long) {
        scope.launch {
          logger.debug("({}) last live time changed to {}", streamer.name, lastLiveTime)
          repo.update(streamer.copy(lastLiveTime = lastLiveTime))
        }
      }

      override fun onDescriptionChanged(streamer: Streamer, description: String) {
        scope.launch {
          logger.debug("({}) description changed to {}", streamer.name, description)
          repo.update(streamer.copy(streamTitle = description))
        }
      }

      override fun onAvatarChanged(streamer: Streamer, avatar: String) {
        scope.launch {
          logger.debug("({}) avatar changed to {}", streamer.name, avatar)
          repo.update(streamer.copy(avatar = avatar))
        }
      }

      override fun onStreamDownloaded(streamer: Streamer, stream: StreamData) {
        scope.launch {
          try {
            val saved = streamDataRepository.save(stream)
            stream.id = saved.id
          } catch (e: Exception) {
            logger.error("Failed to save stream data", e)
          }
          // run post actions
          executePostPartedDownloadActions(streamer, stream)
        }
      }

      override fun onStreamDownloadFailed(streamer: Streamer, stream: StreamData, e: Exception) {

      }

      override fun onStreamFinished(streamer: Streamer, streams: List<StreamData>) {
        scope.launch {
          logger.debug("({}) stream finished", streamer.name)
          executeStreamFinishedActions(streamer, streams)
        }
      }
    }

    val streamers = withIOContext {
      repo.getStreamersActive()
    }
    this.streamers = streamers
    streamers.groupBy { it.platform }.forEach {
      val service = getOrInitPlatformService(it.key)
      it.value.forEach(service::addStreamer)
    }
    // listen to streamer changes
    scope.listenToStreamerChanges()
  }


  private fun getOrInitPlatformService(platform: StreamingPlatform): DownloadPlatformService {
    val fetchDelay = (platform.platformConfig(app.config).fetchDelay ?: 0).toDuration(DurationUnit.SECONDS)
    val service = taskJobs.computeIfAbsent(platform) {
      logger.info("({}) initializing...", platform)
      DownloadPlatformService(
        app,
        scope,
        fetchDelay.inWholeMilliseconds,
        downloadSemaphore,
        callback,
        platform,
        PlatformDownloaderFactory
      )
    }
    return service
  }


  private fun CoroutineScope.listenToStreamerChanges() {
    launch {
      repo.stream().distinctUntilChanged().buffer().collectLatest { streamerList ->
        logger.info("Streamers changed, reloading...")

        // compare the new streamers with the old ones, first by url, then by entity equals
        // if a stream is not in the new list, cancel the job
        // if a stream is in the new list but not in the old one, start a new job
        // if a stream is in both lists, do nothing

        if (streamerList.isEmpty()) {
          logger.info("No streamers to download")
          // the new list is empty, cancel all jobs
          taskJobs.keys.forEach { platform ->
            taskJobs[platform]?.cancel()
          }
          return@collectLatest
        }

        val oldStreamers = streamers

        val newStreamers = streamerList.filterNot { it.isTemplate }
        // cancel the jobs of the streamers that are not in the new list
        if (oldStreamers.isNotEmpty()) {
          oldStreamers.filter { old ->
            newStreamers.none { new -> new.url == old.url }
          }.forEach { streamer ->
            val platform = streamer.platform
            val streamerService = taskJobs[platform] ?: return@forEach
            streamerService.cancelStreamer(streamer, "delete")
          }
        }

        // diff the new streamers with the old ones
        // if a stream has the same url but different entity, cancel the old job and start a new one
        // if a stream is not in the old list, start a new job
        // if a stream is in both lists, do nothing
        newStreamers.forEach { new ->
          val old = oldStreamers.find { it.url == new.url } ?: run {
            if (validateActivation(new)) return@forEach
            // the stream is not in the old list, start a new download job
            val platform = new.platform
            val service = taskJobs[platform] ?: getOrInitPlatformService(platform)
            service.addStreamer(new)
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
              old.templateStreamer?.downloadConfig != new.templateStreamer?.downloadConfig -> "template stream download config"
              // other changes are ignored
              else -> return@forEach
            }
            logger.debug("Detected entity change for {}, {}", new, old)
            val platform = old.platform
            val service = taskJobs[platform] ?: getOrInitPlatformService(platform)
            service.cancelStreamer(old, reason)
            if (validateActivation(new)) return@forEach
            service.addStreamer(new)
          }
        }
        streamers = newStreamers
      }
    }
  }


  /**
   * Validates the activation status of a given [Streamer].
   *
   * @param new The [Streamer] object to validate.
   * @return true if the [Streamer] is not activated, false otherwise.
   */
  private fun validateActivation(new: Streamer): Boolean {
    if (!new.isActivated) {
      logger.debug("${new.name}, ${new.url} is not activated")
      return true
    }
    return false
  }


  private suspend fun executePostPartedDownloadActions(streamer: Streamer, streamData: StreamData) {
    val actions = streamer.templateStreamer?.downloadConfig?.onPartedDownload ?: streamer.downloadConfig?.onPartedDownload ?: return
    actionService.runActions(listOf(streamData), actions)
  }


  private suspend fun executeStreamFinishedActions(streamer: Streamer, streamDataList: List<StreamData>) {
    val actions =
      streamer.templateStreamer?.downloadConfig?.onStreamingFinished ?: streamer.downloadConfig?.onStreamingFinished
    actions?.let {
      actionService.runActions(streamDataList, it)
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

  fun cancel() {
    scope.cancel()
  }
}