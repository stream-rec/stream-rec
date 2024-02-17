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
import github.hua0512.data.StreamData
import github.hua0512.data.Streamer
import github.hua0512.data.StreamingPlatform
import github.hua0512.data.config.Action
import github.hua0512.data.config.CommandAction
import github.hua0512.data.config.RcloneAction
import github.hua0512.data.upload.RcloneConfig
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Download
import github.hua0512.plugins.danmu.douyin.DouyinDanmu
import github.hua0512.plugins.danmu.huya.HuyaDanmu
import github.hua0512.plugins.download.Douyin
import github.hua0512.plugins.download.Huya
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DownloadService(val app: App, val uploadService: UploadService) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(DownloadService::class.java)
  }

  private fun getPlaformDownloader(platform: StreamingPlatform): Download = when (platform) {
    StreamingPlatform.HUYA -> Huya(app, HuyaDanmu(app))
    StreamingPlatform.DOUYIN -> Douyin(app, DouyinDanmu(app))
    else -> throw Exception("Platform not supported")
  }

  private val taskJobs = mutableListOf<Pair<Streamer, Job>>()

  suspend fun run() = coroutineScope {
    launch {
      app.streamersFlow.collect { streamerList ->
        logger.info("Streamers changed, reloading...")

        // compare the new streamers with the old ones, first by url, then by entity equals
        // if a streamer is not in the new list, cancel the job
        // if a streamer is in the new list but not in the old one, start a new job
        // if a streamer is in both lists, do nothing

        if (streamerList.isEmpty()) {
          logger.info("No streamers to download")
          // the new list is empty, cancel all jobs
          taskJobs.forEach { it.second.cancel() }
          return@collect
        }

        val activeStreamers = streamerList.filter { it.isActivated }
        val oldStreamersSet = taskJobs.map { it.first }.toMutableSet()
        for (newStreamer in activeStreamers) {
          val job = taskJobs.find { job -> job.first.url == newStreamer.url }
          // job is non-null if the streamer is in the old list
          if (job != null) {
            val sameJob = taskJobs.find { it.first == newStreamer }

            // if [sameJob] is non-null, then the streamer is in the old list, with same info
            // no update needed
            if (sameJob != null) {
              oldStreamersSet.remove(job.first)
              continue
            }
            // if [sameJob] is null, then the streamer is in the old list, but with different info
            // do nothing, the old job will be canceled and a new one will be started
          }
          // job is null, the streamer is not in the old list
          // This is a new streamer (or maybe an old one but with new info), start a new job
          val newJob = async {
            downloadStreamer(newStreamer)
          }
          taskJobs.add(newStreamer to newJob)
          logger.info("Download for streamer ${newStreamer.name} has been started")
        }

        // Cancel the jobs for streamers that are not in the new list
        for (oldStreamer in oldStreamersSet) {
          val job = taskJobs.find { job -> job.first == oldStreamer }
          job?.second?.cancel()
          taskJobs.remove(job)
          logger.info("Download for streamer ${oldStreamer.name} has been cancelled")
        }
      }
    }
  }

  private suspend fun downloadStreamer(streamer: Streamer) {
    val newJob = SupervisorJob(coroutineContext[Job])
    val newScope = CoroutineScope(coroutineContext + CoroutineName("Streamer-${streamer.name}") + newJob)
    newScope.launch {
      logger.debug("Launching coroutine : {}", this.coroutineContext)
      if (streamer.isLive) {
        logger.info("Streamer ${streamer.name} is already live")
        return@launch
      }
      val plugin = getPlaformDownloader(streamer.platform)

      val streamDataList = mutableListOf<StreamData>()
      var retryCount = 0
      val retryDelay = app.config.downloadRetryDelay
      val maxRetry = app.config.maxDownloadRetries
      while (true) {

        if (retryCount > maxRetry) {
          // stream is not live or without data
          if (streamDataList.isEmpty()) {
            retryCount = 0
            streamer.isLive = false
            continue
          }
          // stream finished with data
          logger.error("(${streamer.name}) max retry reached")
          logger.info("(${streamer.name}) has finished streaming")
          // call onStreamingFinished callback with the copy of the list
          launch {
            bindOnStreamingEndActions(streamer, streamDataList.toList())
          }
          retryCount = 0
          streamer.isLive = false
          streamDataList.clear()
          delay(1.toDuration(DurationUnit.MINUTES))
          continue
        }
        val isLive = try {
          // check if streamer is live
          plugin.shouldDownload(streamer)
        } catch (e: Exception) {
          logger.error("Error while checking if ${streamer.name} is live : ${e.message}")
          false
        }

        if (isLive) {
          streamer.isLive = true
          // stream is live, start downloading
          val streamsData = app.downloadSemaphore.withPermit {
            try {
              plugin.download()
            } catch (e: Exception) {
              logger.error("Error while getting stream data for ${streamer.name} : ${e.message}")
              null
            }
          }
          if (streamsData == null) {
            retryCount++
            logger.error("(${streamer.name}) download data not found")
            continue
          }
          retryCount = 0
          streamDataList.add(streamsData)
          logger.info("(${streamer.name}) downloaded : $streamsData")
          launch { executePostPartedDownloadActions(streamer, streamsData) }
        } else {
          logger.info("Streamer ${streamer.name} is not live")
        }
        retryCount++
        // if a data list is not empty, then it means the stream has ended
        // wait 30 seconds before checking again
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
    val downloadConfig = streamer.downloadConfig
    val onStreamFinishedActions = downloadConfig?.onStreamingFinished
    if (!onStreamFinishedActions.isNullOrEmpty()) {
      onStreamFinishedActions
        .filter { it.enabled }
        .forEach {
          it.mapToAction(streamDataList)
        }
    } else {
      // delete files if both onStreamFinished and onPartedDownload are empty
      if (downloadConfig?.onPartedDownload.isNullOrEmpty() && app.config.deleteFilesAfterUpload) {
        streamDataList.forEach {
          Path(it.outputFilePath).deleteFile()
        }
      }
    }
  }

  private suspend fun executePostPartedDownloadActions(streamer: Streamer, streamData: StreamData) {
    val partedActions = streamer.downloadConfig?.onPartedDownload
    if (!partedActions.isNullOrEmpty()) {
      partedActions
        .filter { it.enabled }
        .forEach { action: Action ->
          action.mapToAction(listOf(streamData))
        }
    }
  }

  private suspend fun Action.mapToAction(streamDataList: List<StreamData>) {
    return when (this) {
      is RcloneAction -> {
        this.run {
          val finalList = streamDataList.flatMap { streamData ->
            listOfNotNull(
              UploadData(0, streamData.title, streamData.streamer.name, streamData.dateStart!!, streamData.outputFilePath),
              streamData.danmuFilePath?.let { UploadData(0, streamData.title, streamData.streamer.name, streamData.dateStart, it) }
            )
          }
          UploadAction(
            id = 0,
            time = System.currentTimeMillis(),
            uploadDataList = finalList,
            uploadConfig = RcloneConfig(
              remotePath = this.remotePath,
              args = this.args
            )
          ).let { uploadService.upload(it) }
        }
      }

      is CommandAction -> {
        this.run {
          logger.info("Running command action : $this")
          val exitCode = suspendCancellableCoroutine<Int> {
            val inputString = streamDataList.joinToString("\n") { it.outputFilePath }
            val processDirectory = app.config.outputFolder.ifEmpty { null }
            val process = ProcessBuilder(this.program, *this.args.toTypedArray())
              .redirectError(ProcessBuilder.Redirect.PIPE)
              .run {
                if (processDirectory != null) {
                  directory(Path(processDirectory).toFile())
                } else {
                  this
                }
              }
              .start()

            val writter = process.outputStream.bufferedWriter()
            // write the list of files to the process input
            writter.use {
              it.write(inputString)
            }
            val job = Job()
            val scope = CoroutineScope(Dispatchers.IO + job)
            scope.launch {
              process.waitFor()
              it.resume(process.exitValue())
            }
            job.invokeOnCompletion {
              process.destroy()
            }
          }
          logger.info("Command action $this finished with exit code $exitCode")
        }
      }

      else -> throw UnsupportedOperationException("Invalid action: $this")
    }

  }

}