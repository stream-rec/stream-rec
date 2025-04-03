package github.hua0512.plugins.file

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.dto.IOutputFile
import github.hua0512.plugins.action.AbstractProcessingPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Base implementation for file operation plugins that handles common functionality
 * such as file locking, retries, and error handling.
 *
 * @param config Configuration for the file operations.
 */
abstract class BaseFileOperationPlugin<T : FileOperationConfig>(
  protected val config: T,
) : AbstractProcessingPlugin<IOutputFile, IOutputFile, FileOperationError>(), FileOperationPlugin {


  companion object {
    protected val logger = LoggerFactory.getLogger(BaseFileOperationPlugin::class.java)
  }


  // Track active file operations to prevent concurrent modifications to the same file
  private val activeOperations = ConcurrentHashMap<String, Boolean>()

  // Compiled pattern if fileFilter is specified
  private val fileFilterPattern: Pattern? by lazy {
    config.fileFilter?.let { Pattern.compile(it) }
  }

  /**
   * Performs the actual file operation on a single file.
   *
   * @param file The file to operate on.
   * @return Result containing output file(s) or error.
   */
  protected abstract suspend fun performOperation(file: IOutputFile): Result<List<IOutputFile>, FileOperationError>

  /**
   * Generates destination path for the file.
   *
   * @param sourceFile The source file.
   * @return The destination path.
   */
  protected abstract fun getDestinationPath(sourceFile: IOutputFile): Path

  override suspend fun processItem(input: IOutputFile): Result<List<IOutputFile>, FileOperationError> {
    // Check if we should process this file based on filter
    if (!shouldProcess(input)) {
      return Ok(listOf(input)) // Skip but pass through
    }

    val sourceFile = File(input.path)
    if (!sourceFile.exists()) {
      return Err(FileOperationError.SourceFileNotFoundError("Source file not found: ${input.path}"))
    }

    // Try to obtain lock on the source file
    val lockResult = tryLockFile(sourceFile.toPath())
    if (lockResult.isErr) {
      return lockResult.asErr()
    }

    val fileLock = lockResult.value

    try {
      // Mark this file as being processed
      activeOperations[sourceFile.absolutePath] = true

      // Try operation with retries
      return retryOperation(input)
    } finally {
      // Release the lock and mark file as no longer being processed
      fileLock?.release()
      activeOperations.remove(sourceFile.absolutePath)
    }
  }

  private suspend fun retryOperation(input: IOutputFile): Result<List<IOutputFile>, FileOperationError> {
    var lastError: FileOperationError? = null

    for (attempt in 0..config.retryCount) {
      if (attempt > 0) {
        delay(config.retryDelayMs)
      }

      val result = if (config.operationTimeoutMs > 0) {
        withTimeoutOrNull(config.operationTimeoutMs) {
          performOperation(input)
        }
      } else {
        performOperation(input)
      }

      when {
        result == null -> lastError =
          FileOperationError.OperationError("Operation timed out after ${config.operationTimeoutMs}ms")

        result.isOk -> return result
        result.isErr -> lastError = result.error
      }
    }

    return Err(lastError ?: FileOperationError.OperationError("Unknown error during $operationType operation"))
  }

  /**
   * Attempts to lock a file to ensure exclusive access.
   *
   * @param path The path to lock.
   * @return Result containing the FileLock if successful, or an error.
   */
  private suspend fun tryLockFile(path: Path): Result<FileLock?, FileOperationError> = withContext(Dispatchers.IO) {
    try {
      if (!path.exists() || !path.isRegularFile()) {
        return@withContext Ok(null) // Nothing to lock
      }

      val channel = FileChannel.open(path, StandardOpenOption.READ)

      try {
        // Try to get a shared read lock (non-exclusive)
        val lock = channel.tryLock(0L, Long.MAX_VALUE, true)
        if (lock == null) {
          channel.close()
          return@withContext Err(FileOperationError.FileLockError("File is locked by another process: $path"))
        }
        return@withContext Ok(lock)
      } catch (e: Exception) {
        channel.close()
        return@withContext Err(FileOperationError.FileLockError("Failed to lock file: $path", e))
      }
    } catch (e: Exception) {
      return@withContext Err(FileOperationError.FileLockError("Error accessing file: $path", e))
    }
  }

  /**
   * Creates parent directories for a given path if they don't exist.
   */
  protected suspend fun ensureParentDirectories(path: Path): Result<Unit, FileOperationError> =
    withContext(Dispatchers.IO) {
      if (!config.createDirectories) {
        if (!path.parent.exists()) {
          return@withContext Err(
            FileOperationError.InvalidDestinationError("Destination directory does not exist: ${path.parent}")
          )
        }
        return@withContext Ok(Unit)
      }

      try {
        Files.createDirectories(path.parent)
        Ok(Unit)
      } catch (e: IOException) {
        Err(FileOperationError.OperationError("Failed to create directories: ${path.parent}", e))
      } catch (e: SecurityException) {
        Err(FileOperationError.PermissionError("Permission denied when creating directories: ${path.parent}", e))
      }
    }

  /**
   * Preserves file attributes from source to destination if configured.
   */
  protected suspend fun preserveAttributes(source: Path, destination: Path): Result<Unit, FileOperationError> =
    withContext(Dispatchers.IO) {
      if (!config.preserveAttributes) return@withContext Ok(Unit)

      try {
        val attrs = Files.readAttributes(source, BasicFileAttributes::class.java)

        // Preserve last modified time
        Files.setLastModifiedTime(destination, attrs.lastModifiedTime())

        // Other attribute preservation can be added here

        Ok(Unit)
      } catch (e: Exception) {
        // Non-critical error, just log and continue
        Err(FileOperationError.OperationError("Failed to preserve attributes: $e", e))
      }
    }

  /**
   * Handles destination conflicts based on configuration.
   *
   * @param destinationPath The destination path where we want to write.
   * @return A potentially modified destination path or an error if the conflict cannot be resolved.
   */
  protected suspend fun handleDestinationConflict(destinationPath: Path): Result<Path, FileOperationError> =
    withContext(Dispatchers.IO) {
      if (!destinationPath.exists()) return@withContext Ok(destinationPath)

      if (config.overwriteExisting) {
        // Delete existing if overwrite is enabled
        try {
          Files.deleteIfExists(destinationPath)
          return@withContext Ok(destinationPath)
        } catch (e: Exception) {
          return@withContext Err(
            FileOperationError.OperationError(
              "Failed to overwrite existing file: $destinationPath", e
            )
          )
        }
      } else {
        return@withContext Err(
          FileOperationError.InvalidDestinationError(
            "Destination file already exists: $destinationPath"
          )
        )
//        // Generate a new filename with a timestamp
//        val now = System.currentTimeMillis()
//        val fileName = destinationPath.fileName.toString()
//        val extension = fileName.substringAfterLast('.', "")
//        val baseName = if (extension.isEmpty()) fileName else fileName.substringBeforeLast('.')
//
//        val newDestination =
//          destinationPath.resolveSibling("${baseName}_$now.${if (extension.isEmpty()) "" else extension}")
//        return@withContext Ok(newDestination)
      }
    }

  override fun shouldProcess(file: IOutputFile): Boolean {
    // Apply filter pattern if configured
    fileFilterPattern?.let { pattern ->
      val fileName = File(file.path).name
      if (!pattern.matcher(fileName).matches()) {
        return false
      }
    }

    return true
  }

  override suspend fun validate(inputs: List<IOutputFile>): Result<Unit, FileOperationError> {
    // Check if any files exist
    if (inputs.isEmpty()) {
      return Err(FileOperationError.ValidationError("No input files provided"))
    }

    // Validate destination
    if (!validateDestination()) {
      return Err(
        FileOperationError.InvalidDestinationError(
          "Destination validation failed for $operationType operation"
        )
      )
    }

    return Ok(Unit)
  }

  /**
   * Create an execution error with the proper type.
   */
  override fun createExecutionError(message: String, cause: Throwable?): FileOperationError {
    return FileOperationError.OperationError(message, cause)
  }

  /**
   * Log successful file operations with timing information.
   */
  override suspend fun onItemSuccess(
    input: IOutputFile,
    outputs: List<IOutputFile>,
    timing: ItemExecutionTiming,
  ) {
    logger.debug("$operationType operation on ${input.path} completed in ${timing.duration}")
  }

  /**
   * Log failed file operations with timing information.
   */
  override suspend fun onItemError(
    input: IOutputFile,
    error: FileOperationError,
    timing: ItemExecutionTiming,
  ) {
    logger.debug("$operationType operation on ${input.path} failed after ${timing.duration}: ${error.message}")
  }
}
