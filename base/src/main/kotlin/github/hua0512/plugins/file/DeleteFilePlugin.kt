package github.hua0512.plugins.file

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginConfigs.DeleteFileConfig
import github.hua0512.data.dto.IOutputFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile


/**
 * Plugin that deletes files.
 */
class DeleteFilePlugin(config: DeleteFileConfig) : BaseFileOperationPlugin<DeleteFileConfig>(config) {


  private companion object {
    private val logger = LoggerFactory.getLogger(DeleteFilePlugin::class.java)
  }

  override val id: String = "f2c6d835-ae3e-48a3-b4c1-1b3c8e293fda"
  override val name: String = "File Delete"
  override val description: String = "Deletes files based on configured criteria"
  override val version: String = "1.0.0"
  override val author: String = "System"

  override val operationType: String = "delete"

  // Return source path as there's no destination
  public override fun getDestinationPath(sourceFile: IOutputFile): Path =
    Path.of(sourceFile.path)

  // No destination validation needed for delete operations
  override suspend fun validateDestination(): Boolean = true

  override fun shouldProcess(file: IOutputFile): Boolean {
    // Apply parent class filter first
    if (!super.shouldProcess(file)) return false

    // Check age threshold if specified
    if (config.olderThanMs > 0) {
      val path = Path.of(file.path)
      if (path.exists()) {
        try {
          val lastModified = Files.getLastModifiedTime(path).toMillis()
          val ageMs = System.currentTimeMillis() - lastModified

          if (ageMs < config.olderThanMs) {
            return false // File is not old enough to delete
          }
        } catch (e: Exception) {
          // If we can't determine age, err on the side of caution and don't delete
          return false
        }
      }
    }

    return true
  }

  public override suspend fun performOperation(file: IOutputFile): Result<List<IOutputFile>, FileOperationError> =
    withContext(Dispatchers.IO) {
      val path = Path.of(file.path)

      if (!path.exists()) {
        return@withContext Err(
          FileOperationError.SourceFileNotFoundError(
            "File not found: $path"
          )
        )
      }

      if (!path.isRegularFile()) {
        return@withContext Err(
          FileOperationError.OperationError(
            "Not a regular file: $path"
          )
        )
      }

      logger.debug("Deleting file: {}", path)

      try {
        if (config.moveToTrash) {
          // Try to move file to trash/recycle bin if supported
          try {
            moveToTrash(path)
          } catch (e: UnsupportedOperationException) {
            // Fall back to regular deletion if trash is not supported
            if (config.secureDelete) {
              secureDelete(path)
            } else {
              Files.delete(path)
            }
          }
        } else if (config.secureDelete) {
          secureDelete(path)
        } else {
          Files.delete(path)
        }
        // Deletion doesn't produce output files
        return@withContext Ok(emptyList())
      } catch (e: IOException) {
        return@withContext Err(
          FileOperationError.OperationError(
            "Failed to delete file: $path", e
          )
        )
      } catch (e: SecurityException) {
        return@withContext Err(
          FileOperationError.PermissionError(
            "Permission denied when deleting file: $path", e
          )
        )
      } catch (e: Exception) {
        return@withContext Err(
          FileOperationError.OperationError(
            "Unexpected error during delete operation: ${e.message}", e
          )
        )
      }
    }

  /**
   * Securely deletes a file by overwriting its contents before deletion.
   *
   * @param path The path to the file to securely delete.
   */
  private suspend fun secureDelete(path: Path): Unit = withContext(Dispatchers.IO) {
    // Get file size
    val fileSize = path.fileSize()

    // Create a buffer for overwriting data (up to 1MB chunks)
    val bufferSize = minOf(fileSize, 1024 * 1024).toInt().coerceAtLeast(1)
    val buffer = ByteBuffer.allocate(bufferSize)

    // Open file for writing
    Files.newByteChannel(
      path,
      StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING
    ).use { channel ->
      // Perform multiple overwrite passes
      repeat(config.secureDeletionPasses) { passIndex ->
        // Fill the buffer with different patterns based on pass
        when (passIndex % 3) {
          0 -> buffer.fill(0x00.toByte())  // All zeros
          1 -> buffer.fill(0xFF.toByte())  // All ones
          2 -> buffer.fill(0x55.toByte())  // Alternating pattern
        }

        // Overwrite the entire file
        var position = 0L
        while (position < fileSize) {
          // Reset buffer position to read from beginning
          buffer.flip()

          // Write the buffer to the file
          val written = channel.write(buffer)
          position += written

          // Clear buffer for next use
          buffer.clear()
        }

        // Force changes to disk
        (channel as? FileChannel)?.force(true)
      }
    }

    // Finally delete the file
    Files.delete(path)
  }

  /**
   * Helper function to fill a ByteBuffer with a specific byte value.
   */
  private fun ByteBuffer.fill(value: Byte) {
    for (i in 0 until capacity()) {
      put(i, value)
    }
  }

  /**
   * Moves a file to the system trash/recycle bin.
   * This implementation uses AWT Desktop if available, or falls back to a custom implementation.
   *
   * @param path The path to move to trash.
   * @throws UnsupportedOperationException if trash operations are not supported.
   */
  private fun moveToTrash(path: Path) {
    try {
      val desktop = java.awt.Desktop.getDesktop()
      if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
        val success = desktop.moveToTrash(path.toFile())
        if (!success) {
          throw IOException("Failed to move file to trash: $path")
        }
        return
      }
    } catch (e: Exception) {
      // Fall through to try alternative implementations
    }

    // Try system-specific implementations
    val osName = System.getProperty("os.name").lowercase()

    when {
      osName.contains("windows") -> {
        // For Windows, use JNA or ProcessBuilder approach
        // This is a simplified example - a real implementation would use JNA to call Windows APIs
        val command = listOf(
          "powershell.exe", "-command",
          "Add-Type -AssemblyName Microsoft.VisualBasic;" +
                  "[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile(" +
                  "'${path.toString().replace("'", "''")}', " +
                  "'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowErrorIfRecycleBinIsFull')"
        )

        val process = ProcessBuilder(command)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
          throw IOException("Failed to move file to trash with exit code $exitCode: $path")
        }
      }

      osName.contains("mac") -> {
        // For macOS
        val command = listOf("osascript", "-e", "tell application \"Finder\" to delete POSIX file \"${path}\"")
        val process = ProcessBuilder(command)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
          throw IOException("Failed to move file to trash with exit code $exitCode: $path")
        }
      }

      osName.contains("linux") || osName.contains("unix") -> {
        // For Linux/Unix systems
        val command = listOf("gio", "trash", path.toString())
        try {
          val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

          val exitCode = process.waitFor()
          if (exitCode != 0) {
            throw IOException("Failed to move file to trash with exit code $exitCode: $path")
          }
        } catch (e: Exception) {
          // Try alternative command
          val altCommand = listOf("trash-put", path.toString())
          val process = ProcessBuilder(altCommand)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

          val exitCode = process.waitFor()
          if (exitCode != 0) {
            throw IOException("Failed to move file to trash with exit code $exitCode: $path")
          }
        }
      }

      else -> {
        throw UnsupportedOperationException("Moving files to trash is not supported on this platform")
      }
    }
  }

  /**
   * Override of onItemSuccess to provide file-specific information.
   */
  override suspend fun onItemSuccess(
    input: IOutputFile,
    outputs: List<IOutputFile>,
    timing: ItemExecutionTiming,
  ) {
    val method = when {
      config.moveToTrash -> "moved to trash"
      config.secureDelete -> "securely deleted"
      else -> "deleted"
    }

    logger.debug("File ${input.path} was successfully $method in ${timing.duration}")
  }
}
