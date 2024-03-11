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
import github.hua0512.utils.replacePlaceholders
import github.hua0512.utils.withIOContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

    if (!uploadData.isStreamDataInitialized()) {
      throw UploadInvalidArgumentsException("stream data not initialized : $uploadData")
    }

    val startInstant =
      Instant.fromEpochSeconds(uploadData.streamStartTime ?: throw UploadInvalidArgumentsException("stream start time not initialized"))
    val streamer = uploadData.streamer
    val replacedRemote = remotePath.run {
      replacePlaceholders(streamer, uploadData.streamTitle, startInstant)
    }

    val rcloneCommand = arrayOf(
      "rclone",
      uploadConfig.rcloneOperation,
      uploadData.filePath,
      replacedRemote
    ) + uploadConfig.args

    logger.debug("Processing {}...", rcloneCommand.toList())
    val errorBuilder = StringBuilder()
    // rclone "operation" <local file> <remote folder> --args
    val resultCode = executeProcess(*rcloneCommand, stdout = Redirect.SILENT, stderr = Redirect.CAPTURE, consumer = {
      logger.info(it)
      errorBuilder.append(it)
    })

    if (resultCode != 0) {
      throw UploadFailedException("rclone failed with exit code: $resultCode\n$errorBuilder", uploadData.filePath)
    } else {
      logger.info("rclone: ${uploadData.filePath} finished")
      UploadResult(time = Clock.System.now().epochSeconds, isSuccess = true)
    }
  }
}