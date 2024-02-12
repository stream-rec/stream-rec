package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.data.upload.RcloneConfig
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadConfig
import github.hua0512.plugins.upload.RcloneUploader
import kotlinx.coroutines.Dispatchers
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
        uploader.upload(uploadAction.uploadDataList)
      }
      .flowOn(Dispatchers.IO)
      .catch {
        logger.error("Error in upload flow: $it")
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