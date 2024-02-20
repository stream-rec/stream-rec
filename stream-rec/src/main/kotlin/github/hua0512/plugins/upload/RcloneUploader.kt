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

import github.hua0512.app.App
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult
import github.hua0512.plugins.base.Upload
import github.hua0512.utils.executeProcess
import github.hua0512.utils.process.Redirect
import github.hua0512.utils.withIOContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RcloneUploader(app: App, override val uploadConfig: UploadConfig.RcloneConfig) : Upload(app, uploadConfig) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(RcloneUploader::class.java)
  }


  override suspend fun upload(uploadData: UploadData): UploadResult = withIOContext {
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
    val startTimeString = Instant.fromEpochMilliseconds(uploadData.streamStartTime).toLocalDateTime(TimeZone.currentSystemDefault())
    val streamer = uploadData.streamer

    val toReplace: Map<String, String> = mapOf(
      "{streamer}" to streamer,
      "{title}" to uploadData.streamTitle,
      "%yyyy" to startTimeString.year.toString(),
      "%MM" to startTimeString.monthNumber.toString(),
      "%dd" to startTimeString.dayOfMonth.toString(),
      "%HH" to startTimeString.hour.toString(),
      "%mm" to startTimeString.minute.toString(),
      "%ss" to startTimeString.second.toString(),
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

    val resultCode = executeProcess(*finalCmds, stdout = Redirect.CAPTURE, consumer = {
      logger.debug(it)
    })

    if (resultCode != 0) {
      throw UploadFailedException("rclone failed with exit code: $resultCode", uploadData.filePath)
    } else {
      logger.info("rclone: ${uploadData.filePath} finished")
      UploadResult(time = Clock.System.now().epochSeconds, isSuccess = true, filePath = uploadData.filePath)
    }
  }
}