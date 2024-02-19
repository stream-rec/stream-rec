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
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadConfig.NoopConfig
import github.hua0512.data.upload.UploadConfig.RcloneConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult
import github.hua0512.plugins.base.Upload
import github.hua0512.plugins.upload.NoopUploader
import github.hua0512.plugins.upload.RcloneUploader
import github.hua0512.plugins.upload.UploadInvalidArgumentsException
import github.hua0512.repo.UploadActionRepository
import github.hua0512.utils.deleteFile
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

class UploadService(val app: App, val uploadRepo: UploadActionRepository) {

  val uploadActionFlow = MutableSharedFlow<UploadAction>(replay = 1)


  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(UploadService::class.java)
  }

  suspend fun run() = coroutineScope {
    launch {
      uploadActionFlow
        .onEach { action ->
          val uploader = provideUploader(action.uploadConfig)
          val results = parallelUpload(action.files, uploader)

          // delete files after upload
          if (app.config.deleteFilesAfterUpload) {
            results.forEach {
              // save the result
              uploadRepo.saveResult(it)
              if (it.isSuccess) {
                // get the upload data and update the status
                val uploadDataId = it.uploadDataId
                uploadRepo.changeUploadDataStatus(uploadDataId, true)
                Path.of(it.filePath).deleteFile()
              }
            }
          }

          // ignore saving if its [NoopUploader]
          if (uploader is NoopUploader) {
            return@onEach
          }
          logger.debug("Upload results: {}", results)
        }
        .collect()
    }
    // launch a coroutine scanning for failed uploads
    launch {
//      uploadRepo.uploadDataSubscription()
//        .onEach { _ ->
//          val failedUploads = uploadRepo.getFailedUpdloadDataList()
//          failedUploads.forEach {
//
//            val linkedAction = uploadRepo.get(it.uploadActionId!!)
//            if (linkedAction == null) {
//              logger.error("Failed to get linked action for id: ${it.uploadActionId}")
//              return@forEach
//            }
//            val linkedConfig = linkedAction.uploadConfig
//            val linkedPlatform = linkedConfig.platform
//          }
//        }
//        .collect()

    }

  }

  suspend fun upload(uploadAction: UploadAction) {
    val saved = withIOContext {
      uploadRepo.save(uploadAction)
    }
    uploadAction.id = saved.value
    uploadActionFlow.emit(uploadAction)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun parallelUpload(files: Collection<UploadData>, plugin: Upload): List<UploadResult> = coroutineScope {
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
        file.status = status.isSuccess
        status.uploadDataId = file.id
        success = true
        logger.info("Successfully uploaded file: ${file.filePath}")
        emit(status)
      } catch (e: UploadInvalidArgumentsException) {
        logger.error("Invalid arguments for upload: ${file.filePath}")
        emit(
          UploadResult(
            time = Clock.System.now().epochSeconds, isSuccess = false, message = "Invalid arguments for upload",
            filePath = file.filePath
          ).also { it.uploadDataId = file.id }
        )
      } catch (e: Exception) {
        // other exceptions,
        // most likely network issues or an UploadFailedException
        attempts++
        logger.error("Failed to upload file: ${file.filePath}, attempt: $attempts", e)
        if (attempts >= 3) {
          emit(
            UploadResult(
              time = Clock.System.now().epochSeconds,
              isSuccess = false,
              message = "Failed to upload file : ${e.cause}",
              filePath = file.filePath
            ).also { it.uploadDataId = file.id })
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