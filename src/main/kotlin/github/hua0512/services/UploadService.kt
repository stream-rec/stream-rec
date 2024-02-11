package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.data.UploadAction
import github.hua0512.data.UploadPlatform
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
        uploadAction.uploadDataList.forEach {
          logger.info("Uploading: $it")
          val uploader = provideUploader(it.platform)
          uploader.upload(listOf(it))
        }
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


  private fun provideUploader(platform: UploadPlatform) = when (platform) {
    UploadPlatform.RCLONE -> RcloneUploader(app)
    else -> throw IllegalArgumentException("Invalid uploader: $platform")
  }
}