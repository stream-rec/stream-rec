package github.hua0512.plugins.file

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.PluginConfigs.MoveFileConfig
import github.hua0512.data.dto.IOutputFile
import github.hua0512.utils.substringBeforePlaceholders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists


/**
 * Plugin that moves files from source to destination.
 */
class MoveFilePlugin(
  config: MoveFileConfig,
) : BaseFileOperationPlugin<MoveFileConfig>(config) {

  override val id: String = "9efd7218-14a5-4972-83f2-6a961a9b0bc5"
  override val name: String = "File Move"
  override val description: String = "Moves files to a configured destination"
  override val version: String = "1.0.0"
  override val author: String = "System"
  override val operationType: String = "move"

  override suspend fun validateDestination(): Boolean = withContext(Dispatchers.IO) {
    val destDir = File(config.destinationDirectory.substringBeforePlaceholders())

    // Check if destination exists or can be created
    if (!destDir.exists()) {
      if (!config.createDirectories) return@withContext false

      try {
        destDir.mkdirs()
      } catch (e: Exception) {
        return@withContext false
      }
    }

    // Check if it's a directory and writable
    return@withContext destDir.isDirectory && destDir.canWrite()
  }

  override fun getDestinationPath(sourceFile: IOutputFile): Path {
    val destDir = Path.of(
      config.destinationDirectory.replacePlaceholders(sourceFile)
    )
    val targetDir = destDir

    // Use the original filename
    val sourceFilename = Path.of(sourceFile.path).fileName.toString()
    return targetDir.resolve(sourceFilename)
  }

  public override suspend fun performOperation(file: IOutputFile): Result<List<IOutputFile>, FileOperationError> =
    withContext(Dispatchers.IO) {
      val sourcePath = Path.of(file.path)
      if (!sourcePath.exists()) {
        return@withContext Err(
          FileOperationError.SourceFileNotFoundError(
            "Source file not found: $sourcePath"
          )
        )
      }

      var destinationPath = getDestinationPath(file)

      // Handle destination conflicts
      val conflictResult = handleDestinationConflict(destinationPath)
      if (conflictResult.isErr) return@withContext conflictResult.asErr()
      destinationPath = conflictResult.value

      logger.debug("Moving file from {} to {}", sourcePath, destinationPath)

      // Ensure parent directories exist
      val dirResult = ensureParentDirectories(destinationPath)
      if (dirResult.isErr) return@withContext dirResult.asErr()

      try {
        // Try an atomic move first (most efficient)
        try {
          Files.move(
            sourcePath,
            destinationPath,
            StandardCopyOption.ATOMIC_MOVE
          )
        } catch (e: IOException) {
          // If atomic move failed, fall back to copy+delete
          Files.copy(
            sourcePath,
            destinationPath,
            StandardCopyOption.REPLACE_EXISTING
          )

          // Preserve attributes if configured
          preserveAttributes(sourcePath, destinationPath)

          // Try to delete the original file
          try {
            Files.delete(sourcePath)
          } catch (deleteEx: Exception) {
            // Log but continue since the file was successfully copied
            logger.error("Warning: File was copied but original could not be deleted: $sourcePath")
          }
        }

        // update file path
        file.path = destinationPath.toString()
        // return the moved file
        Ok(listOf(file))
      } catch (e: IOException) {
        Err(
          FileOperationError.OperationError(
            "Failed to move file from $sourcePath to $destinationPath", e
          )
        )
      } catch (e: SecurityException) {
        Err(
          FileOperationError.PermissionError(
            "Permission denied when moving file to $destinationPath", e
          )
        )
      } catch (e: Exception) {
        Err(
          FileOperationError.OperationError(
            "Unexpected error during move operation: ${e.message}", e
          )
        )
      }
    }
}
