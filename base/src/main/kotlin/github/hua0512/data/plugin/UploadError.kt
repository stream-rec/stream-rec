package github.hua0512.data.plugin

/**
 * Specific error types for upload operations.
 */
sealed class UploadError : PluginError {
  /**
   * Error that occurs when file validation fails.
   */
  data class ValidationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs when authentication fails.
   */
  data class AuthenticationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs during the upload process.
   */
  data class TransferError(
    override val message: String,
    val statusCode: Int? = null,
    val response: String? = null,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs when the upload quota is exceeded.
   */
  data class QuotaExceededError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs when the upload times out.
   */
  data class TimeoutError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs due to server issues.
   */
  data class ServerError(
    override val message: String,
    val statusCode: Int? = null,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs when the connection is lost.
   */
  data class ConnectionError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs when configuration is invalid.
   */
  data class ConfigurationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : UploadError()

  /**
   * Error that occurs for other reasons.
   */
  data class UnknownError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : UploadError()
}