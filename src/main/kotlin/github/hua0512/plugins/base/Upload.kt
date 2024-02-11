package github.hua0512.plugins.base

import github.hua0512.UploadResult
import github.hua0512.app.App
import github.hua0512.data.UploadData

abstract class Upload(protected val app: App) {
  abstract suspend fun upload(data: List<UploadData>): List<UploadResult>
}