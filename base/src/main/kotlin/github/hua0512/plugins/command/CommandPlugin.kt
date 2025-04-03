package github.hua0512.plugins.command

import github.hua0512.data.plugin.PluginError
import github.hua0512.data.dto.IOutputFile
import github.hua0512.plugins.action.ProcessingPlugin

/**
 * Base interface for command execution plugins.
 */
interface CommandPlugin : ProcessingPlugin<IOutputFile, IOutputFile, CommandError> {

  /**
   * Returns the type of command this plugin executes.
   */
  val commandType: String

  /**
   * Whether this command should run once for all inputs or once per input.
   * - true: Run the command once with all inputs as arguments
   * - false: Run the command separately for each input
   */
  val batchMode: Boolean

  /**
   * Maximum number of concurrent commands that can be running at once.
   * Used to prevent resource exhaustion.
   */
  val maxConcurrentCommands: Int
}

/**
 * Specific error type for command execution errors.
 */
sealed class CommandError : PluginError {

  /**
   * Error that occurs during command execution.
   */
  data class ExecutionError(
    override val message: String,
    val exitCode: Int? = null,
    val stderr: String? = null,
    override val cause: Throwable? = null,
  ) : CommandError()

  /**
   * Error that occurs when command times out.
   */
  data class TimeoutError(
    override val message: String,
    val command: List<String>,
  ) : CommandError()

  /**
   * Error that occurs when validation fails.
   */
  data class ValidationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CommandError()

  /**
   * Error that occurs when configuration is invalid.
   */
  data class ConfigurationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CommandError()

  /**
   * Error that occurs due to permission or security restrictions.
   */
  data class SecurityError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CommandError()
}
