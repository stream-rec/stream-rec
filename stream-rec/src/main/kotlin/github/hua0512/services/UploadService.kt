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
import github.hua0512.plugins.event.EventCenter
import github.hua0512.plugins.upload.NoopUploader
import github.hua0512.plugins.upload.base.Upload
import github.hua0512.plugins.upload.exceptions.UploadFailedException
import github.hua0512.plugins.upload.exceptions.UploadInvalidArgumentsException
import github.hua0512.repo.upload.UploadRepo
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
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
 * It also contains a flow of upload actions and a map to keep track of failed upload and the number of times they failed (in-memory).
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
   * A semaphore to limit the number of concurrent upload.
   */
  private val uploadSemaphore: Semaphore by lazy { Semaphore(app.config.maxConcurrentUploads) }

  /**
   * Uploads an upload action.
   * This function saves the upload action to the repository and emits it to the upload action flow.
   *
   * @param uploadAction The upload action to upload.
   */
  suspend fun upload(uploadAction: UploadAction) {
    val saved = withIOContext {
      uploadRepo.saveAction(uploadAction)
    }
    val uploader = PlatformUploaderFactory.create(app, uploadAction.uploadConfig)
    if (uploader is NoopUploader) {
      return
    }
    val deferredResults = CompletableDeferred<List<UploadResult>>()
    withIOContext {
      val results = parallelUpload(saved.files, uploader)
      logger.debug("Upload results: {}", results)
      deferredResults.complete(results)
    }

    deferredResults.await()
    // throw exception with exception message of the first failed upload
    deferredResults.getCompleted().firstOrNull { !it.isSuccess }?.let {
      throw UploadFailedException(it.message ?: "", it.filePath.toString())
    }
  }


  /**
   * Uploads a collection of files in parallel.
   * This function uses the provided plugin to upload each file in the collection.
   * The number of concurrent upload is determined by the application's configuration.
   *
   * @param files The collection of files to upload.
   * @param plugin The plugin to use for uploading the files.
   * @return A list of upload results.
   */
  private suspend fun <T : UploadConfig> parallelUpload(files: Collection<UploadData>, plugin: Upload<T>): List<UploadResult> = coroutineScope {
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
  private fun <T : UploadConfig> uploadFileFlow(file: UploadData, plugin: Upload<T>): Flow<UploadResult> = flow {
    var attempts = 0
    // copy the file to update the status
    var newFile = file.copy(status = UploadState.UPLOADING)
    uploadRepo.updateUploadData(newFile)
    EventCenter.sendEvent(UploadEvent.UploadStart(newFile.filePath, newFile.uploadPlatform, Clock.System.now()))
    // Retry logic
    while (attempts < 3) {
      try {
        if (attempts > 0 && newFile.status != UploadState.REUPLOADING) {
          // change the status to REUPLOADING if the upload is being retried
          newFile = newFile.copy(status = UploadState.REUPLOADING)
          uploadRepo.updateUploadData(newFile)
          EventCenter.sendEvent(UploadEvent.UploadRetry(newFile.filePath, newFile.uploadPlatform, Clock.System.now(), attempts))
        } else if (attempts > 0) {
          // send retry event
          EventCenter.sendEvent(UploadEvent.UploadRetry(newFile.filePath, newFile.uploadPlatform, Clock.System.now(), attempts))
        }

        // upload the file
        uploadSemaphore.withPermit {
          val status = plugin.upload(newFile)
          uploadRepo.updateUploadData(file.copy(status = UploadState.UPLOADED))
          EventCenter.sendEvent(UploadEvent.UploadSuccess(newFile.filePath, newFile.uploadPlatform, Clock.System.now()))
          logger.info("Successfully uploaded file: ${newFile.filePath}")
          emit(status)
          attempts = 3
        }
      } catch (e: UploadInvalidArgumentsException) {
        logger.error("Invalid arguments for upload file: ${newFile.filePath}")
        EventCenter.sendEvent(UploadEvent.UploadFailure(newFile.filePath, newFile.uploadPlatform, Clock.System.now(), e))
        emit(
          UploadResult(
            startTime = Clock.System.now().epochSeconds,
            isSuccess = false,
            message = "${e.message}",
            uploadDataId = newFile.id,
            uploadData = newFile
          ).apply {
            newFile = newFile.copy(status = UploadState.FAILED)
            uploadRepo.updateUploadData(newFile)
          }
        )
        attempts = 3
      } catch (e: Exception) {
        // other exceptions,
        // most likely network issues or an UploadFailedException
        attempts++
        logger.error("Failed to upload file: ${newFile.filePath}, attempt: $attempts", e)
        EventCenter.sendEvent(UploadEvent.UploadFailure(newFile.filePath, newFile.uploadPlatform, Clock.System.now(), e))
        if (attempts >= 3) {
          newFile = newFile.copy(status = UploadState.FAILED)
          uploadRepo.updateUploadData(newFile)
          emit(
            UploadResult(
              startTime = Clock.System.now().epochSeconds,
              isSuccess = false,
              message = "${e.message}",
              uploadDataId = newFile.id,
              uploadData = newFile,
            )
          )
        }
      }
    }
  }
}