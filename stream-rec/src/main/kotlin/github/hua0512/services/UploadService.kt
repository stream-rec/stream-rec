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
import github.hua0512.data.event.UploadEvent
import github.hua0512.data.upload.*
import github.hua0512.data.upload.UploadConfig.NoopConfig
import github.hua0512.data.upload.UploadConfig.RcloneConfig
import github.hua0512.plugins.upload.base.Upload
import github.hua0512.plugins.event.EventCenter
import github.hua0512.plugins.upload.NoopUploader
import github.hua0512.plugins.upload.RcloneUploader
import github.hua0512.plugins.upload.UploadFailedException
import github.hua0512.plugins.upload.UploadInvalidArgumentsException
import github.hua0512.repo.uploads.UploadRepo
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Service class for managing upload actions.
 *
 * This class provides methods for running the upload service, uploading an upload action, and providing an uploader based on the upload configuration.
 * It also contains a flow of upload actions and a map to keep track of failed uploads and the number of times they failed (in-memory).
 *
 * @property app The application instance.
 * @property uploadRepo The repository for managing upload actions.
 *
 * @author hua0512
 * @date : 2024/2/19 15:30
 */
class UploadService(val app: App, private val uploadRepo: UploadRepo) {

  companion object {
    /**
     * Logger for this class.
     */
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(UploadService::class.java)
  }

  /**
   * A semaphore to limit the number of concurrent uploads.
   */
  private val uploadSemaphore: Semaphore by lazy { Semaphore(app.config.maxConcurrentUploads) }

  /**
   * Uploads an upload action.
   * This function saves the upload action to the repository and emits it to the upload action flow.
   *
   * @param uploadAction The upload action to upload.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun upload(uploadAction: UploadAction) {
    val saved = withIOContext {
      uploadRepo.saveAction(uploadAction)
    }
    uploadAction.id = saved.value
    val uploader = provideUploader(uploadAction.uploadConfig)
    if (uploader is NoopUploader) {
      return
    }
    val deferredResults = CompletableDeferred<List<UploadResult>>()
    withIOContext {
      val results = parallelUpload(uploadAction.files, uploader)
      logger.debug("Upload results: {}", results)
      deferredResults.complete(results)
    }

    deferredResults.await()
    // throw exception with exception message of the first failed upload
    deferredResults.getCompleted().firstOrNull { !it.isSuccess }?.let {
      throw UploadFailedException(it.message, it.filePath)
    }
  }


  /**
   * Uploads a collection of files in parallel.
   * This function uses the provided plugin to upload each file in the collection.
   * The number of concurrent uploads is determined by the application's configuration.
   *
   * @param files The collection of files to upload.
   * @param plugin The plugin to use for uploading the files.
   * @return A list of upload results.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun parallelUpload(files: Collection<UploadData>, plugin: Upload): List<UploadResult> = coroutineScope {
    files.asFlow()
      .flatMapMerge(concurrency = app.config.maxConcurrentUploads) { file ->
        uploadFileFlow(file, plugin)
      }
      .onEach {
        uploadRepo.saveResult(it)
      }
      .flowOn(Dispatchers.IO)
      .toList()
  }

  /**
   * Uploads a file and emits the result.
   * This function attempts to upload the file up to three times. If the upload fails three times, it emits a failed upload result.
   *
   * @param file The file to upload.
   * @param plugin The plugin to use for uploading the file.
   * @return A flow of upload results.
   */
  private fun uploadFileFlow(file: UploadData, plugin: Upload): Flow<UploadResult> = flow {
    var attempts = 0
    // change the status to UPLOADING
    file.status = UploadState.UPLOADING
    uploadRepo.changeUploadDataStatus(file.id, file.status)
    EventCenter.sendEvent(UploadEvent.UploadStart(file.filePath, file.uploadPlatform, Clock.System.now()))
    // Retry logic
    while (attempts < 3) {
      try {
        if (attempts > 0 && file.status != UploadState.REUPLOADING) {
          // change the status to REUPLOADING if the upload is being retried
          file.status = UploadState.REUPLOADING
          uploadRepo.changeUploadDataStatus(file.id, file.status)
          EventCenter.sendEvent(UploadEvent.UploadRetry(file.filePath, file.uploadPlatform, Clock.System.now(), attempts))
        } else if (attempts > 0 && file.status == UploadState.REUPLOADING) {
          EventCenter.sendEvent(UploadEvent.UploadRetry(file.filePath, file.uploadPlatform, Clock.System.now(), attempts))
        }

        // upload the file
        uploadSemaphore.withPermit {
          val status = plugin.upload(file).apply {
            uploadData = file
          }
          file.status = UploadState.UPLOADED
          file.uploadResults.add(status)
          uploadRepo.changeUploadDataStatus(file.id, file.status)
          EventCenter.sendEvent(UploadEvent.UploadSuccess(file.filePath, file.uploadPlatform, Clock.System.now()))
          logger.info("Successfully uploaded file: ${file.filePath}")
          emit(status)
          attempts = 3
        }
      } catch (e: UploadInvalidArgumentsException) {
        logger.error("Invalid arguments for upload: ${file.filePath}")
        EventCenter.sendEvent(UploadEvent.UploadFailure(file.filePath, file.uploadPlatform, Clock.System.now(), e))
        emit(
          UploadResult(
            startTime = Clock.System.now().epochSeconds, isSuccess = false, message = "Invalid arguments for upload: ${e.message}",
          ).apply {
            uploadData = file
            file.status = UploadState.FAILED
            file.uploadResults.add(this)
            uploadRepo.changeUploadDataStatus(file.id, file.status)
          }
        )
        attempts = 3
      } catch (e: Exception) {
        // other exceptions,
        // most likely network issues or an UploadFailedException
        attempts++
        logger.error("Failed to upload file: ${file.filePath}, attempt: $attempts", e)
        EventCenter.sendEvent(UploadEvent.UploadFailure(file.filePath, file.uploadPlatform, Clock.System.now(), e))
        if (attempts >= 3) {
          emit(
            UploadResult(
              startTime = Clock.System.now().epochSeconds,
              isSuccess = false,
              message = "Failed to upload file : $e",
            ).apply {
              uploadData = file
              file.status = UploadState.FAILED
              file.uploadResults.add(this)
              uploadRepo.changeUploadDataStatus(file.id, file.status)
            })
        }
      }
    }
  }

  /**
   * Provides an uploader based on the upload configuration.
   * This function returns an uploader based on the type of the upload configuration.
   *
   * @param config The upload configuration.
   * @return An uploader.
   * @throws IllegalArgumentException If the upload configuration is invalid.
   */
  private fun provideUploader(config: UploadConfig) = when (config) {
    is RcloneConfig -> RcloneUploader(app, config)
    is NoopConfig -> NoopUploader(app)
    else -> throw IllegalArgumentException("Invalid config: $config")
  }
}