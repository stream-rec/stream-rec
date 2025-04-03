package github.hua0512.plugins.command

/**
 * Base configuration for command plugins.
 */
interface CommandConfig {

  /**
   * Timeout in milliseconds for command execution.
   */
  val timeoutMs: Long

  /**
   * Working directory for the command.
   * If null, the current working directory is used.
   */
  val workingDirectory: String?

  /**
   * Environment variables to set for the command.
   * These are added to the existing environment variables.
   */
  val environmentVariables: Map<String, String>

  /**
   * Whether to redirect stdout to a file.
   */
  val redirectOutput: Boolean

  /**
   * Whether to redirect stderr to a file.
   */
  val redirectError: Boolean

  /**
   * Pattern to filter input files.
   * Format depends on implementation, could be glob, regex, etc.
   */
  val fileFilter: String?

  /**
   * Number of retries for failed commands.
   */
  val retryCount: Int

  /**
   * Delay between retries in milliseconds.
   */
  val retryDelayMs: Long

  /**
   * Maximum allowed characters for command output.
   * Used to prevent memory issues with very large outputs.
   */
  val maxOutputSize: Int

  /**
   * Whether to treat non-zero exit codes as errors.
   */
  val failOnNonZeroExit: Boolean

  /**
   * List of allowed commands (for security).
   * If empty, all commands are allowed.
   */
  val allowedCommands: List<String>
}
