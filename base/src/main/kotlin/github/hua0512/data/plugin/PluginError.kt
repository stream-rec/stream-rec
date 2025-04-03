package github.hua0512.data.plugin

/**
 * Base interface for all plugin errors.
 */
interface PluginError {
  /**
   * Error message.
   */
  val message: String

  /**
   * Optional cause of the error.
   */
  val cause: Throwable?
    get() = null
}

/**
 * Standard error types for plugins.
 */
sealed class StandardPluginError : PluginError {
  /**
   * Error that occurs during validation.
   */
  data class ValidationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : StandardPluginError()

  /**
   * Error that occurs during execution.
   */
  data class ExecutionError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : StandardPluginError()

  /**
   * Error that occurs when the plugin is misconfigured.
   */
  data class ConfigurationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : StandardPluginError()
}
