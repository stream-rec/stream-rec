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

  suspend fun run() = coroutineScope {
    val streamers = app.config.streamers
    val tasks = streamers.filter {
      !it.isLive && it.isActivated
    }.map {
      logger.info("Checking streamer ${it.name}")
      async {
        logger.debug("Adding job, current context : {}", coroutineContext)
        downloadStreamer(it)
      }
    }
    awaitAll(*tasks.toTypedArray())
  }

  private suspend fun downloadStreamer(streamer: Streamer) {
    val job = coroutineContext[Job]
    logger.debug("current job : {}", job)
    val coroutineName = CoroutineName("Streamer-${streamer.name}")
    val newContext = coroutineContext + coroutineName
    logger.debug("New context : {}", newContext)
    val newJob = SupervisorJob(job)
    newJob.invokeOnCompletion {
      logger.debug("Job completed : $it")
    }
    val newScope = CoroutineScope(newContext + newJob)
    logger.info("Starting download for streamer ${streamer.name}")
    newScope.launch {
      logger.debug("Launching coroutine : {}", this.coroutineContext)
      if (streamer.isLive) {
        logger.info("Streamer ${streamer.name} is already live")
        return@launch
      }
      val plugin = getPlaformDownloader(streamer.platform)

      // bind onPartedDownload actions
      bindOnPartedDownloadActions(streamer, plugin)

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
          logger.error("Max retry reached for ${streamer.name}")
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
              emptyList()
            }
          }
          retryCount = 0
          logger.info("Final stream data : $streamsData")
          if (streamsData.isEmpty()) {
            logger.error("No data found for ${streamer.name}")
            continue
          }
          streamDataList.addAll(streamsData)
          logger.info("Stream for ${streamer.name} has ended")
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

  private fun bindOnPartedDownloadActions(streamer: Streamer, plugin: Download) {
    val partedActions = streamer.downloadConfig?.onPartedDownload
    if (!partedActions.isNullOrEmpty()) {
      plugin.onPartedDownload = {
        partedActions
          .filter { it.enabled }
          .forEach { action: Action ->
            action.mapToAction(listOf(it))
          }
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
            val streamDataList = streamDataList.joinToString("\n") { it.outputFilePath }

            val process = ProcessBuilder(this.program, *this.args.toTypedArray())
              .redirectError(ProcessBuilder.Redirect.PIPE)
              .start()
            val writter = process.outputStream.bufferedWriter()
            // write the list of files to the process input
            writter.use {
              it.write(streamDataList)
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