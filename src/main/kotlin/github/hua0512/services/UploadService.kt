package github.hua0512.services

import github.hua0512.data.upload.UploadResult
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