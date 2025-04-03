package github.hua0512.plugins.file

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.PluginConfigs.CopyFileConfig
import github.hua0512.data.dto.IOutputFile
import github.hua0512.utils.substringBeforePlaceholders
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists


/**
 * Plugin that copies files from source to destination.
 */
class CopyFilePlugin(
  config: CopyFileConfig,
) : BaseFileOperationPlugin<CopyFileConfig>(config) {

  override val id: String = "95b572f4-283e-4d6f-9ece-c6136beb8aaf"
  override val name: String = "File Copy"
  override val description: String = "Copies files to a configured destination"
  override val version: String = "1.0.0"
  override val author: String = "System"
  override val operationType: String = "copy"

  override suspend fun validateDestination(): Boolean = withIOContext {
    val destDir = File(config.destinationDirectory.substringBeforePlaceholders())

    // Check if destination exists or can be created
    if (!destDir.exists()) {
      if (!config.createDirectories) return@withIOContext false

      try {
        destDir.mkdirs()
      } catch (e: Exception) {
        return@withIOContext false
      }
    }

    // Check if it's a directory and writable
    return@withIOContext destDir.isDirectory && destDir.canWrite()
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

      logger.debug("Copying file from {} to {}", sourcePath, destinationPath)

      // Ensure parent directories exist
      val dirResult = ensureParentDirectories(destinationPath)
      if (dirResult.isErr) return@withContext dirResult.asErr()

      try {
        // Perform the actual copy
        Files.copy(
          sourcePath,
          destinationPath,
          if (config.overwriteExisting) {
            StandardCopyOption.REPLACE_EXISTING
          } else {
            StandardCopyOption.COPY_ATTRIBUTES
          }
        )

        // Preserve attributes if configured
        preserveAttributes(sourcePath, destinationPath)

        // ignore copy file
        Ok(listOf(file))
      } catch (e: IOException) {
        Err(
          FileOperationError.OperationError(
            "Failed to copy file from $sourcePath to $destinationPath", e
          )
        )
      } catch (e: SecurityException) {
        Err(
          FileOperationError.PermissionError(
            "Permission denied when copying file to $destinationPath", e
          )
        )
      } catch (e: Exception) {
        Err(
          FileOperationError.OperationError(
            "Unexpected error during copy operation: ${e.message}", e
          )
        )
      }
    }
}
