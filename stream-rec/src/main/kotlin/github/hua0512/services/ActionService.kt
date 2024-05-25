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
import github.hua0512.data.stream.StreamData
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.utils.*
import github.hua0512.utils.process.InputSource
import github.hua0512.utils.process.Redirect
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.moveTo
import kotlin.io.path.name

/**
 * ActionService is responsible for running actions on the stream data
 * @author hua0512
 * @date : 2024/3/17 19:45
 */
class ActionService(private val app: App, private val uploadService: UploadService) {


  companion object {
    private val logger: Logger = LoggerFactory.getLogger(ActionService::class.java)
  }

  suspend fun runActions(streamDataList: List<StreamData>, actions: List<Action>) = withIOContext {
    actions.filter {
      it.enabled
    }.forEach { action ->
      try {
        val job = async {
          action.mapToAction(streamDataList)
        }
        job.await()
      } catch (e: Exception) {
        logger.error("$streamDataList, error while executing action $action : ${e.message}")
        return@withIOContext
      }
    }
  }


  private suspend fun Action.mapToAction(streamDataList: List<StreamData>) {
    val dataList = streamDataList.flatMap { streamData ->
      listOfNotNull(
        UploadData(
          filePath = streamData.outputFilePath,
          streamData = streamData,
        ),
        streamData.danmuFilePath?.let { danmu ->
          UploadData(
            filePath = danmu,
            streamData = streamData
          )
        }
      )
    }
    if (dataList.isEmpty()) {
      logger.error("No files to process for action $this")
      return
    }

    when (this) {
      is RcloneAction -> {
        UploadAction(
          time = Clock.System.now().toEpochMilliseconds(),
          files = dataList,
          uploadConfig = UploadConfig.RcloneConfig(
            rcloneOperation = this.rcloneOperation,
            remotePath = this.remotePath,
            args = this.args
          )
        ).let { uploadService.upload(it) }
      }

      is CommandAction -> {
        this.apply {
          logger.info("Running command action : $this")

          val streamData = streamDataList.first()
          val streamer = streamData.streamer ?: throw IllegalStateException("Streamer not found for stream data $streamData")
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
          val sb = StringBuilder().apply {
            dataList.forEach {
              append(it.filePath).append("\n")
            }
          }
          // execute the command
          val exitCode = executeProcess(
            this.program, *this.args.toTypedArray(),
            stdin = InputSource.fromString(sb.toString()),
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
        dataList.forEach { data ->
          val file = Path(data.filePath)
          val destFile = dest.resolve(file.name).apply {
            createParentDirectories()
          }
          file.moveTo(destFile)
          logger.info("Moved $file to $destFile")
        }
      }

      is RemoveAction -> {
        dataList.forEach { data ->
          val file = Path(data.filePath)
          file.deleteFile()
        }
      }

      else -> throw UnsupportedOperationException("Invalid action: $this")
    }
  }

}