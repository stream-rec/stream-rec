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

package github.hua0512.plugins.upload

import github.hua0512.data.upload.UploadResult
import github.hua0512.app.App
import github.hua0512.data.upload.RcloneConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

class RcloneUploader(app: App, override val uploadConfig: RcloneConfig) : Upload(app, uploadConfig) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(RcloneUploader::class.java)
  }


  override suspend fun upload(uploadData: UploadData): UploadResult = withContext(Dispatchers.IO) {
    val remotePath = uploadConfig.remotePath.also {
      if (it.isEmpty()) {
        throw UploadInvalidArgumentsException("invalid remote path: $it")
      }
    }
    // rclone copy <local file> <remote folder>
    val cmds = arrayOf(
      "rclone",
      uploadConfig.rcloneOperation,
    )
    val extraCmds = uploadConfig.args.toTypedArray()

    // rclone "operation" <local file> <remote folder> --args
    // format dateStart to local time
    val startTimeString = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(uploadData.streamStartTime),
      ZoneId.systemDefault()
    )
    val streamer = uploadData.streamer

    val toReplace = mapOf(
      "{streamer}" to streamer,
      "{title}" to uploadData.streamTitle,
      "%yyyy" to startTimeString.format(DateTimeFormatter.ofPattern("yyyy")),
      "%MM" to startTimeString.format(DateTimeFormatter.ofPattern("MM")),
      "%dd" to startTimeString.format(DateTimeFormatter.ofPattern("dd")),
      "%HH" to startTimeString.format(DateTimeFormatter.ofPattern("HH")),
      "%mm" to startTimeString.format(DateTimeFormatter.ofPattern("mm")),
      "%ss" to startTimeString.format(DateTimeFormatter.ofPattern("ss")),
    )

    val replacedRemote = remotePath.run {
      var result = this
      toReplace.forEach { (k, v) ->
        result = result.replace(k, v)
      }
      result
    }
    val finalCmds = cmds + arrayOf(
      uploadData.filePath,
      replacedRemote
    ) + extraCmds

    val resultCode = suspendCancellableCoroutine {
      val builder = ProcessBuilder(*finalCmds)
        .redirectErrorStream(true)
        .start()

      it.invokeOnCancellation {
        builder.destroy()
      }
      launch {
        builder.inputStream.bufferedReader().readText().let { line ->
          logger.debug(line)
        }
      }

      val code = builder.waitFor()
      it.resume(code)
    }

    if (resultCode != 0) {
      throw UploadFailedException("rclone failed with exit code: $resultCode", uploadData.filePath)
    } else {
      logger.info("rclone: ${uploadData.filePath} finished")
      UploadResult(System.currentTimeMillis(), true, "", uploadData.filePath)
    }
  }
}