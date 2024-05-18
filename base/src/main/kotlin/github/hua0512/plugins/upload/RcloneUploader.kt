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
import github.hua0512.data.upload.UploadConfig.RcloneConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult
import github.hua0512.plugins.upload.base.Upload
import github.hua0512.plugins.upload.exceptions.UploadFailedException
import github.hua0512.plugins.upload.exceptions.UploadInvalidArgumentsException
import github.hua0512.utils.executeProcess
import github.hua0512.utils.nonEmptyOrNull
import github.hua0512.utils.process.Redirect
import github.hua0512.utils.replacePlaceholders
import github.hua0512.utils.withIOContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RcloneUploader(app: App, override val config: RcloneConfig) : Upload<RcloneConfig>(app, config) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(RcloneUploader::class.java)
  }


  override suspend fun performUpload(uploadData: UploadData): UploadResult = withIOContext {
    val remotePath = config.remotePath.nonEmptyOrNull() ?: throw UploadInvalidArgumentsException("invalid rclone remote path")

    val startInstant = Instant.fromEpochSeconds(uploadData.streamStartTime)
    val streamer = uploadData.streamer
    val streamTitle = uploadData.streamTitle

    val replacedRemote = remotePath.replacePlaceholders(streamer, streamTitle, startInstant)

    val rcloneCommand = arrayOf(
      "rclone",
      config.rcloneOperation,
      uploadData.filePath,
      replacedRemote
    ) + config.args

    logger.debug("Processing {}...", rcloneCommand.toList())
    val errorBuilder = StringBuilder()
    val startTime = Clock.System.now()
    // rclone "operation" <local file> <remote folder> --args
    val resultCode = executeProcess(*rcloneCommand, stdout = Redirect.SILENT, stderr = Redirect.CAPTURE, consumer = {
      logger.info(it)
      errorBuilder.append(it)
    })

    if (resultCode != 0) {
      throw UploadFailedException("$errorBuilder", uploadData.filePath)
    } else {
      logger.info("rclone: ${uploadData.filePath} finished")
      UploadResult(
        startTime = startTime.epochSeconds,
        endTime = Clock.System.now().epochSeconds,
        isSuccess = true,
        uploadDataId = uploadData.id,
        uploadData = uploadData,
      )
    }
  }
}