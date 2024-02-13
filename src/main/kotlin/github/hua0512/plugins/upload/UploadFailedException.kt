package github.hua0512.plugins.upload

class UploadFailedException(val filePath: String, override val message: String) : Exception() {
}