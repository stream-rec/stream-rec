package github.hua0512.plugins.upload

import github.hua0512.UploadResult
import github.hua0512.app.App
import github.hua0512.data.UploadData
import github.hua0512.logger
import github.hua0512.plugins.base.Upload

/**
 * @author hua0512
 * @date : 2024/2/9 2:41
 */
class NoopUploader(app: App) : Upload(app) {

  override suspend fun upload(data: List<UploadData>): List<UploadResult> {
    logger.info("Noop upload: $data")
    return emptyList()
  }
}