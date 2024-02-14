package github.hua0512.plugins.upload

/**
 * @author hua0512
 * @date : 2024/2/13 19:04
 */
class UploadInvalidArgumentsException : IllegalArgumentException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
}