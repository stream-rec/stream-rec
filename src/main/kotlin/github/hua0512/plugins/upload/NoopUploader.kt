package github.hua0512.plugins.upload

import github.hua0512.UploadResult
import github.hua0512.app.App
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Upload
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * @author hua0512
 * @date : 2024/2/9 2:41
 */
class NoopUploader(app: App) : Upload(app, null) {
  override suspend fun uploadAction(uploadData: UploadData): Deferred<UploadResult?> = coroutineScope {
    async {
      null
    }
  }

  /**
   *
   */
  override suspend fun upload(uploadDataList: List<UploadData>): List<UploadResult> = emptyList()
}