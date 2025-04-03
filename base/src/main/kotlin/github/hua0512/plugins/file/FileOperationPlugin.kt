package github.hua0512.plugins.file

import github.hua0512.data.plugin.PluginError
import github.hua0512.data.dto.IOutputFile
import github.hua0512.plugins.action.ProcessingPlugin

/**
 * Base interface for file operation plugins like copy, move, delete.
 */
interface FileOperationPlugin : ProcessingPlugin<IOutputFile, IOutputFile, FileOperationError> {
  /**
   * Check if the file operation should be performed on the given file.
   * Allows plugins to filter which files they should process.
   *
   * @param file The file to check.
   * @return True if the operation should be performed on this file.
   */
  fun shouldProcess(file: IOutputFile): Boolean

  /**
   * Validate that the destination is ready and available for file operations.
   *
   * @return True if the destination is valid and can be written to.
   */
  suspend fun validateDestination(): Boolean

  /**
   * Returns the name of the operation this plugin performs, e.g., "copy", "move", "delete".
   */
  val operationType: String
}

/**
 * Specific error type for file operation errors.
 */
sealed class FileOperationError : PluginError {
  /**
   * Error that occurs when trying to access a file that is locked or in use.
   */
  data class FileLockError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : FileOperationError()

  /**
   * Error that occurs when the source file doesn't exist.
   */
  data class SourceFileNotFoundError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : FileOperationError()

  /**
   * Error that occurs when the destination is invalid.
   */
  data class InvalidDestinationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : FileOperationError()

  /**
   * Error that occurs during the file operation.
   */
  data class OperationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : FileOperationError()

  /**
   * Error that occurs when configuration is invalid.
   */
  data class ConfigurationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : FileOperationError()

  /**
   * Error that occurs when permissions are insufficient.
   */
  data class PermissionError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : FileOperationError()

  /**
   * Error that occurs during validation.
   */
  data class ValidationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : FileOperationError()
}
