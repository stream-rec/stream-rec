package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.data.upload.RcloneConfig
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadConfig
import github.hua0512.plugins.upload.RcloneUploader
import github.hua0512.plugins.upload.UploadFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class UploadService(val app: App) {

  val uploadFlow = MutableSharedFlow<UploadAction>(replay = 1)

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(UploadService::class.java)
  }

  suspend fun run() {
    uploadFlow
      .onEach { uploadAction ->
        logger.info("Received upload action: $uploadAction")
        val uploader = provideUploader(uploadAction.uploadConfig)
        // do not catch exception here, let the retryWhen handle it
        uploader.upload(uploadAction.uploadDataList)
      }
      .buffer(3)
      .flowOn(Dispatchers.IO)
      .retryWhen { cause, attempt ->
        logger.error("Error in upload flow: $cause, retrying attempt: $attempt")

        if (cause !is UploadFailedException) {
          logger.error("Not an UploadFailedException, skipping")
          return@retryWhen false
        }

        if (attempt > 3) {
          logger.error("Failed to upload ${cause.filePath} after 3 attempts, skipping")
          return@retryWhen false
        }
        // delay 30 seconds before retry
        delay(30000)
        true
      }
      .collect()
  }

  fun upload(uploadAction: UploadAction) {
    uploadFlow.tryEmit(uploadAction)
  }

  private fun provideUploader(config: UploadConfig) = when (config) {
    is RcloneConfig -> RcloneUploader(app, config)
    else -> throw IllegalArgumentException("Invalid config: $config")
  }
}