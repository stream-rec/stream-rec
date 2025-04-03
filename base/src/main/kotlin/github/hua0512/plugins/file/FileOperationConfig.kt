package github.hua0512.plugins.file

/**
 * Base configuration for file operation plugins.
 */
interface FileOperationConfig {
  /**
   * When true, will overwrite existing files at destination.
   * When false, will rename or skip existing files depending on implementation.
   */
  val overwriteExisting: Boolean

  /**
   * Timeout in milliseconds for file operations before giving up.
   */
  val operationTimeoutMs: Long

  /**
   * Number of retries for failed operations.
   */
  val retryCount: Int

  /**
   * Delay between retries in milliseconds.
   */
  val retryDelayMs: Long

  /**
   * Whether to create parent directories if they don't exist.
   */
  val createDirectories: Boolean

  /**
   * Whether to preserve file attributes (timestamps, permissions, etc.) when possible.
   */
  val preserveAttributes: Boolean

  /**
   * Filter to determine which files should be processed based on patterns.
   * Format depends on implementation, could be glob, regex, etc.
   */
  val fileFilter: String?
}
