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
import github.hua0512.data.config.Action
import github.hua0512.data.config.Action.*
import github.hua0512.data.dto.GlobalPlatformConfig
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadConfig.RcloneConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Download
import github.hua0512.plugins.danmu.douyin.DouyinDanmu
import github.hua0512.plugins.danmu.huya.HuyaDanmu
import github.hua0512.plugins.download.Douyin
import github.hua0512.plugins.download.Huya
import github.hua0512.repo.streamer.StreamDataRepo
import github.hua0512.repo.streamer.StreamerRepo
import github.hua0512.utils.*
import github.hua0512.utils.process.InputSource
import github.hua0512.utils.process.Redirect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.io.path.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DownloadService(
  private val app: App,
  private val uploadService: UploadService,
  private val repo: StreamerRepo,
  private val streamDataRepository: StreamDataRepo,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(DownloadService::class.java)
  }

  private fun getPlaformDownloader(platform: StreamingPlatform): Download = when (platform) {
    StreamingPlatform.HUYA -> Huya(app, HuyaDanmu(app))
    StreamingPlatform.DOUYIN -> Douyin(app, DouyinDanmu(app))
    else -> throw Exception("Platform not supported")
  }

  private val StreamingPlatform.platformConfig: GlobalPlatformConfig
    get() {
      return when (this) {
        StreamingPlatform.HUYA -> app.config.huyaConfig
        StreamingPlatform.DOUYIN -> app.config.douyinConfig
        else -> throw UnsupportedOperationException("Platform not supported")
      }
    }

  private val taskJobs = mutableMapOf<Streamer, Job?>()

  suspend fun run() = coroutineScope {
    // fetch all streamers from the database and start a job for each one
    repo.getStreamersActive().forEach { streamer ->
      taskJobs[streamer] = async { downloadStreamer(streamer) }
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
          val old = oldStreamers.find { it.url == new.url }
          if (old != null) {
            // if the entity is different, cancel the old job and start a new one
            if (old != new) {
              // find the change reason
              val reason = when {
                old.isActivated != new.isActivated -> "activation"
                old.url != new.url -> "url"
                old.downloadConfig != new.downloadConfig -> "download config"
                old.platform != new.platform -> "platform"
                old.name != new.name -> "name"
                old.templateStreamer?.id != new.templateStreamer?.id -> "template"
                old.templateStreamer?.downloadConfig != new.templateStreamer?.downloadConfig -> "template download config"
                // other changes are ignored
                else -> return@forEach
              }
              logger.debug("Detected entity change for {}, {}", new, old)
              cancelJob(old, "entity changed : $reason")
              if (validateActivation(new)) return@forEach
              startDownloadJob(new)
            }
          } else {
            // if the streamer is not in the old list, start a new job
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
      val plugin = getPlaformDownloader(streamer.platform)

      val streamDataList = mutableListOf<StreamData>()
      var retryCount = 0
      val retryDelay = app.config.downloadRetryDelay
      val maxRetry = app.config.maxDownloadRetries
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
          // call onStreamingFinished callback with the copy of the list
          newScope.launch {
            bindOnStreamingEndActions(streamer, streamDataList.toList())
          }
          streamDataList.clear()
          delay(1.toDuration(DurationUnit.MINUTES))
          continue
        }
        val oldStreamer = streamer.copy()
        val isLive = try {
          // check if streamer is live
          plugin.shouldDownload(streamer)
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
            repo.updateStreamerLiveStatus(streamer.id, true)
          }
          if (oldStreamer.streamTitle != streamer.streamTitle) {
            repo.updateStreamerStreamTitle(streamer.id, streamer.streamTitle)
          }
          streamer.isLive = true

          // update avatar if it has changed
          if (!streamer.avatar.isNullOrEmpty() && oldStreamer.avatar != streamer.avatar) {
            repo.updateStreamerAvatar(streamer.id, streamer.avatar)
          }
          val now = Clock.System.now()
          // update last live time
          if (repo.shouldUpdateStreamerLastLiveTime(streamer.id, streamer.lastLiveTime ?: 0, now.epochSeconds)) {
            repo.updateStreamerLastLiveTime(streamer.id, now.epochSeconds)
            streamer.lastLiveTime = now.epochSeconds
          }
          // stream is live, start downloading
          // while loop for parting the download
          while (true) {
            val streamsData = app.downloadSemaphore.withPermit {
              try {
                plugin.download()
              } catch (e: Exception) {
                when (e) {
                  is IllegalArgumentException -> {
                    logger.error("${streamer.name} invalid url or invalid streamer : ${e.message}")
                    return@launch
                  }

                  is UnsupportedOperationException -> {
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
            if (streamsData == null) {
              logger.error("${streamer.name} unable to get stream data (${retryCount + 1}/$maxRetry)")
              break
            }
            // save the stream data to the database
            try {
              // get file size
              val fileSize = Path(streamsData.outputFilePath).fileSize()
              streamsData.outputFileSize = fileSize
              streamDataRepository.saveStreamData(streamsData)
              logger.debug("saved to db : {}", streamsData)
            } catch (e: Exception) {
              logger.error("${streamer.name} error while saving $streamsData : ${e.message}")
            }
            streamDataList.add(streamsData)
            logger.info("${streamer.name} downloaded : $streamsData}")
            newScope.launch { executePostPartedDownloadActions(streamer, streamsData) }
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
        // if a data list is not empty, then it means the stream has ended
        // wait [retryDelay] seconds before checking again
        // otherwise wait 1 minute
        val duration = if (streamDataList.isNotEmpty()) {
          retryDelay.toDuration(DurationUnit.SECONDS)
        } else {
          1.toDuration(DurationUnit.MINUTES)
        }
        delay(duration)
      }
    }
  }

  private suspend fun bindOnStreamingEndActions(streamer: Streamer, streamDataList: List<StreamData>) {
    val actions = streamer.templateStreamer?.downloadConfig?.onStreamingFinished ?: streamer.downloadConfig?.onStreamingFinished
    actions?.let {
      runActions(streamDataList, it)
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
      runActions(listOf(streamData), it)
    }
  }

  private suspend fun runActions(streamDataList: List<StreamData>, actions: List<Action>) = withIOContext {
    actions.filter {
      it.enabled
    }.forEach { action ->
      val job = async {
        action.mapToAction(streamDataList)
      }
      try {
        job.await()
      } catch (e: Exception) {
        logger.error("$streamDataList, error while executing action $action : ${e.message}")
        return@forEach
      }
    }
  }

  private suspend fun Action.mapToAction(streamDataList: List<StreamData>) {
    when (this) {
      is RcloneAction -> {
        this.run {
          val finalList = streamDataList.flatMap { streamData ->
            listOfNotNull(
              UploadData(
                filePath = streamData.outputFilePath
              ).also {
                it.streamDataId = streamData.id
                it.streamData = streamData
              },
              streamData.danmuFilePath?.let { danmu ->
                UploadData(
                  filePath = danmu
                ).also {
                  it.streamDataId = streamData.id
                  it.streamData = streamData
                }
              }
            )
          }
          UploadAction(
            time = Clock.System.now().toEpochMilliseconds(),
            files = finalList.toSet(),
            uploadConfig = RcloneConfig(
              rcloneOperation = this.rcloneOperation,
              remotePath = this.remotePath,
              args = this.args
            )
          ).let { uploadService.upload(it) }
        }
      }

      is CommandAction -> {
        this.apply {
          logger.info("Running command action : $this")

          val streamData = streamDataList.first()
          val streamer = streamData.streamer
          val downloadConfig = streamer.templateStreamer?.downloadConfig ?: streamer.downloadConfig
          val downloadOutputFolder: File? = (downloadConfig?.outputFolder?.nonEmptyOrNull() ?: app.config.outputFolder).let {
            val instant = Instant.fromEpochSeconds(streamData.dateStart!!)
            val path = it.replacePlaceholders(streamer.name, streamData.title, instant)
            Path(path).toFile().also { file ->
              // if the folder does not exist, then it should be an error
              if (!file.exists()) {
                logger.error("Output folder $this does not exist")
                return@let null
              }
            }
          }
          // files + danmu files
          val finalList: String = streamDataList.flatMap { stream ->
            listOfNotNull(
              stream.outputFilePath,
              stream.danmuFilePath
            )
          }.run {
            if (this.isEmpty()) {
              logger.error("No files to process")
              throw IllegalStateException("No files to process for action $this for streamer $streamer")
            }
            StringBuilder().apply {
              this.forEach {
                append(it)
                append("\n")
              }
            }.toString()
          }
          // execute the command
          val exitCode = executeProcess(
            this.program, *this.args.toTypedArray(),
            stdin = InputSource.fromString(finalList),
            stdout = Redirect.CAPTURE,
            stderr = Redirect.CAPTURE,
            directory = downloadOutputFolder,
            destroyForcibly = true,
            consumer = { line ->
              logger.info(line)
            }
          )
          logger.info("Command action $this finished with exit code $exitCode")
        }
      }

      is MoveAction -> {
        val dest = Path(this.destination)
        streamDataList.flatMap {
          listOfNotNull(
            it.outputFilePath,
            it.danmuFilePath
          )
        }.forEach { filePath ->
          val file = Path(filePath)
          val destFile = dest.resolve(file.name).apply {
            createParentDirectories()
          }
          file.moveTo(destFile)
          logger.info("Moved $file to $destFile")
        }
      }

      is RemoveAction -> {
        streamDataList.flatMap {
          listOfNotNull(
            it.outputFilePath,
            it.danmuFilePath
          )
        }.forEach { filePath ->
          val file = Path(filePath)
          file.deleteFile()
        }
      }

      else -> throw UnsupportedOperationException("Invalid action: $this")
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
  private fun cancelJob(streamer: Streamer, reason: String = ""): Streamer? {
    return taskJobs.keys.find { it.url == streamer.url }?.apply {
      val job = taskJobs[this]
      job?.cancel().also {
        logger.info("${this.name}, ${this.url} job cancelled : $reason")
      }
      taskJobs.remove(this)
    }
  }

}