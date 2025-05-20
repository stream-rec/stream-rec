/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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
import github.hua0512.data.event.Event
import github.hua0512.data.event.UploadEvent
import github.hua0512.data.event.UploadEvent.UploadRetriggered
import github.hua0512.data.upload.*
import github.hua0512.plugins.event.BaseEventPlugin
import github.hua0512.plugins.event.EventCenter
import github.hua0512.plugins.upload.NoopUploader
import github.hua0512.plugins.upload.base.Upload
import github.hua0512.plugins.upload.exceptions.UploadFailedException
import github.hua0512.plugins.upload.exceptions.UploadInvalidArgumentsException
import github.hua0512.repo.upload.UploadRepo
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Service class for managing upload actions.
 *
 * This class provides methods for running the upload service, uploading an upload action, and providing an uploader based on the upload configuration.
 *
 * @property app The application instance.
 * @property uploadRepo The repository for managing upload actions.
 *
 * @author hua0512
 * @date : 2024/2/19 15:30
 */
class UploadService(val app: App, private val uploadRepo: UploadRepo) : BaseEventPlugin() {

  companion object {
    /**
     * Logger for this class.
     */
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(UploadService::class.java)

    // Constants for upload configuration
    private const val MAX_RETRY_ATTEMPTS = 3
  }

  /**
   * A semaphore to limit the number of concurrent upload.
   */
  private val uploadSemaphore: Semaphore by lazy { Semaphore(app.config.maxConcurrentUploads) }


  init {
    EventCenter.subscribe(UploadRetriggered::class, this)
  }

  override suspend fun onEvent(event: Event) {
    event as UploadRetriggered
    event.uploadData.uploadAction?.let { action ->
      PlatformUploaderFactory.create(app, action.uploadConfig).takeIf { it !is NoopUploader }?.let { uploader ->
        parallelUpload(listOf(event.uploadData), uploader)
      }
    } ?: logger.error("Upload action not found for upload data: ${event.uploadData.id}")
  }

  override fun cleanUp() {
  }

  /**
   * Uploads an upload action.
   * This function saves the upload action to the repository and emits it to the upload action flow.
   *
   * @param uploadAction The upload action to upload.
   */
  suspend fun upload(uploadAction: UploadAction) = withIOContext {
    val saved = uploadRepo.saveAction(uploadAction)

    PlatformUploaderFactory.create(app, uploadAction.uploadConfig).takeIf { it !is NoopUploader }?.let { uploader ->
      val results = parallelUpload(saved.files, uploader)
      logger.debug("Upload results: {}", results)

      // Throw exception for first failure if any
      results.firstOrNull { !it.isSuccess }?.let {
        throw UploadFailedException(it.message ?: "", it.filePath.toString())
      }
    }
  }


  /**
   * Uploads a collection of files in parallel.
   * This function uses the provided plugin to upload each file in the collection.
   *
   * @param files The collection of files to upload.
   * @param plugin The plugin to use for uploading the files.
   * @return A list of upload results.
   */
  private suspend fun <T : UploadConfig> parallelUpload(
    files: Collection<UploadData>,
    plugin: Upload<T>,
  ): List<UploadResult> = withIOContext {
    files.map { uploadFile(it, plugin) }
      .onEach { uploadRepo.saveResult(it) }
  }

  /**
   * Uploads a file and emits the result.
   * This function attempts to upload the file up to three times. If the upload fails three times, it emits a failed upload result.
   *
   * @param file The file to upload.
   * @param plugin The plugin to use for uploading the file.
   * @return An upload result
   */
  private suspend fun <T : UploadConfig> uploadFile(file: UploadData, plugin: Upload<T>): UploadResult =
    uploadSemaphore.withPermit {
      var currentFile = file.copy(status = UploadState.UPLOADING).also {
        uploadRepo.updateUploadData(it)
        notifyUploadStart(it)
      }

      for (attempt in 1..MAX_RETRY_ATTEMPTS) {
        try {
          handleRetryAttempt(attempt, currentFile)

          // Perform upload
          return plugin.upload(currentFile).also {
            updateUploadSuccess(currentFile)
            logger.info("Successfully uploaded file: ${currentFile.filePath}")
          }
        } catch (e: UploadInvalidArgumentsException) {
          logger.error("Invalid arguments for upload file: ${currentFile.filePath}")
          return handleUploadError(currentFile, e)
        } catch (e: Exception) {
          logger.error("Failed to upload file: ${currentFile.filePath}, attempt: $attempt", e)
          currentFile = handleUploadFailure(currentFile, e)

          // If this was the last attempt, return error result
          if (attempt == MAX_RETRY_ATTEMPTS) {
            return handleUploadError(currentFile, e)
          }
        }
      }

      throw UploadFailedException("Failed to upload file: ${currentFile.filePath}", currentFile.filePath)
    }

  private suspend fun handleRetryAttempt(attempt: Int, file: UploadData) {
    if (attempt > 1) {
      if (file.status != UploadState.REUPLOADING) {
        file.copy(status = UploadState.REUPLOADING).also {
          uploadRepo.updateUploadData(it)
        }
      }
      notifyUploadRetry(file, attempt)
    }
  }

  private suspend fun updateUploadSuccess(file: UploadData) {
    uploadRepo.updateUploadData(file.copy(status = UploadState.UPLOADED))
    EventCenter.sendEvent(UploadEvent.UploadSuccess(file.filePath, file.uploadPlatform, Clock.System.now()))
  }

  private suspend fun handleUploadFailure(file: UploadData, error: Exception): UploadData {
    EventCenter.sendEvent(
      UploadEvent.UploadFailure(
        file.filePath,
        file.uploadPlatform,
        Clock.System.now(),
        error
      )
    )
    return file.copy(status = UploadState.FAILED).also {
      uploadRepo.updateUploadData(it)
    }
  }

  private fun handleUploadError(file: UploadData, error: Exception): UploadResult {
    return UploadResult(
      startTime = Clock.System.now().epochSeconds,
      isSuccess = false,
      message = error.message ?: "Unknown error",
      uploadDataId = file.id,
      uploadData = file
    )
  }

  private suspend fun notifyUploadStart(file: UploadData) {
    EventCenter.sendEvent(UploadEvent.UploadStart(file.filePath, file.uploadPlatform, Clock.System.now()))
  }

  private suspend fun notifyUploadRetry(file: UploadData, attempt: Int) {
    EventCenter.sendEvent(
      UploadEvent.UploadRetry(
        file.filePath,
        file.uploadPlatform,
        Clock.System.now(),
        attempt
      )
    )
  }
}