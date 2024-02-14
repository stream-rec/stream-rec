package github.hua0512.plugins.base

import github.hua0512.app.App
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult

abstract class Upload(protected val app: App, open val uploadConfig: UploadConfig?) {

  abstract suspend fun upload(uploadData: UploadData): UploadResult
}