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
import github.hua0512.data.upload.*
import github.hua0512.plugins.base.Upload
import github.hua0512.plugins.upload.NoopUploader
import github.hua0512.plugins.upload.RcloneUploader
import github.hua0512.plugins.upload.UploadInvalidArgumentsException
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

class UploadService(val app: App) {

  val uploadActionFlow = MutableSharedFlow<UploadAction>(replay = 1)


  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(UploadService::class.java)

    @JvmStatic
    val failedUploads = mutableSetOf<String>()
  }

  suspend fun run() = withContext(Dispatchers.IO) {
    uploadActionFlow
      .onEach { action ->
        val uploader = provideUploader(action.uploadConfig)
        val results = parallelUpload(action.uploadDataList, uploader)
        results.forEach {
          if (it.isSuccess && app.config.deleteFilesAfterUpload) {
            Path.of(it.filePath).deleteFile()
          } else {
            failedUploads.add(it.filePath)
          }
        }
        logger.debug("Upload results: {}", results)
      }
      .launchIn(this)
  }

  suspend fun upload(uploadAction: UploadAction) {
    uploadActionFlow.emit(uploadAction)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun parallelUpload(files: List<UploadData>, plugin: Upload): List<UploadResult> = coroutineScope {
    files.asFlow()
      .flatMapMerge(concurrency = app.config.maxConcurrentUploads) { file ->
        uploadFileFlow(file, plugin)
      }
      .toList()
  }

  private fun uploadFileFlow(file: UploadData, plugin: Upload): Flow<UploadResult> = flow {
    var attempts = 0
    var success = false
    // Retry logic
    while (attempts < 3 && !success) {
      try {
        // upload the file
        val status = plugin.upload(file)
        success = true
        logger.info("Successfully uploaded file: ${file.filePath}")
        emit(status)
      } catch (e: UploadInvalidArgumentsException) {
        logger.error("Invalid arguments for upload: ${file.filePath}")
        emit(UploadResult(-1, false, "Invalid arguments for upload", file.filePath))
      } catch (e: Exception) {
        // other exceptions,
        // most likely network issues or an UploadFailedException
        attempts++
        logger.error("Failed to upload file: ${file.filePath}, attempt: $attempts", e)
        if (attempts >= 3) {
          emit(UploadResult(-1, false, "Failed to upload file", file.filePath))
        }
      }
    }
  }

  private fun provideUploader(config: UploadConfig) = when (config) {
    is RcloneConfig -> RcloneUploader(app, config)
    is NoopConfig -> NoopUploader(app)
    else -> throw IllegalArgumentException("Invalid config: $config")
  }
}