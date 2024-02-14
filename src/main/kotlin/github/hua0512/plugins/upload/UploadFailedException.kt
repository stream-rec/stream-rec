package github.hua0512.plugins.upload

class UploadFailedException(override val message: String, val filePath: String) : Exception()