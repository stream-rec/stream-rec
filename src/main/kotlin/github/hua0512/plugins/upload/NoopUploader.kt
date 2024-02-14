package github.hua0512.plugins.upload

import github.hua0512.data.upload.UploadResult
import github.hua0512.app.App
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Upload

/**
 * @author hua0512
 * @date : 2024/2/9 2:41
 */
class NoopUploader(app: App) : Upload(app, null) {
  override suspend fun upload(uploadData: UploadData): UploadResult {
    return UploadResult(-1, true, "Noop upload completed", uploadData.filePath)
  }

}