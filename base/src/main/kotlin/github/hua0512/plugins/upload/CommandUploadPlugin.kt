package github.hua0512.plugins.upload

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.plugin.ExecutionTiming
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginConfigs.UploadConfig.CommandUploadConfig
import github.hua0512.data.plugin.UploadError
import github.hua0512.data.plugin.UploadPluginResult
import github.hua0512.data.dto.IOutputFile
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.upload.UploadRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL


/**
 * Upload plugin that uses external commands to perform uploads.
 */
class CommandUploadPlugin(
  config: CommandUploadConfig,
  uploadSemaphore: Semaphore,
  streamRepo: StreamDataRepo,
  uploadRepository: UploadRepo,
) : BaseUploadPlugin<CommandUploadConfig>(config, uploadSemaphore, streamRepo, uploadRepository) {


  override val id: String = "command-upload"
  override val name: String = "Command Upload"
  override val description: String = "Uploads files using external commands"
  override val version: String = "1.0.0"
  override val author: String = "System"
  override val uploadServiceType: String = "command"

  override suspend fun testConnection(): Boolean {
    val testCommand = config.testConnectionCommand ?: return true // No test command means success

    return withContext(Dispatchers.IO) {
      try {
        val process = createProcess(testCommand)
        val exitCode = process.waitFor()
        exitCode == 0
      } catch (e: Exception) {
        false
      }
    }
  }

  override suspend fun authenticate(): Boolean {
    val authCommand = config.authCommand ?: return true // No auth command means success

    return withContext(Dispatchers.IO) {
      try {
        val process = createProcess(authCommand)
        val exitCode = process.waitFor()
        exitCode == 0
      } catch (e: Exception) {
        false
      }
    }
  }

  override suspend fun performUpload(file: IOutputFile): Result<UploadPluginResult, UploadError> =
    withContext(Dispatchers.IO) {
      val localFile = File(file.path)
      if (!localFile.exists()) {
        return@withContext Err(UploadError.ValidationError("File not found: ${file.path}"))
      }

      try {
        val command = buildUploadCommand(file)
        val startTime = System.currentTimeMillis()

        val process = createProcess(command)

        // Capture stdout and stderr
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }

        val exitCode = process.waitFor()
        val endTime = System.currentTimeMillis()

        if (exitCode != 0) {
          return@withContext Err(
            UploadError.TransferError(
              message = "Upload command failed with exit code $exitCode",
              statusCode = exitCode,
              response = stderr
            )
          )
        }

        // Parse URL and remote ID from output if patterns are provided
        val url = config.urlExtractionPattern?.let { pattern ->
          val regex = Regex(pattern)
          val matchResult = regex.find(stdout)
          matchResult?.groupValues?.get(1)?.let { URL(it) }
        }

        val remoteId = config.remoteIdExtractionPattern?.let { pattern ->
          val regex = Regex(pattern)
          val matchResult = regex.find(stdout)
          matchResult?.groupValues?.get(1)
        }

        val result = UploadPluginResult(
          success = true,
          url = url,
          remoteId = remoteId,
          size = localFile.length(),
          uploadTimeMs = endTime - startTime,
          metadata = mapOf(
            "command" to command,
            "stdout" to stdout,
            "stderr" to stderr
          )
        )

        Ok(result)
      } catch (e: Exception) {
        Err(UploadError.UnknownError("Error executing upload command: ${e.message}", e))
      }
    }

  private fun buildUploadCommand(file: IOutputFile): String {
    var command = config.uploadCommandTemplate.replace("{file}", file.path)

    // Add upload command arguments
    if (config.uploadCommandArgs.isNotEmpty()) {
      command += " " + config.uploadCommandArgs.joinToString(" ")
    }

    return command
  }

  private fun createProcess(command: String): Process {
    val processBuilder = if (config.useShell) {
      val isWindows = System.getProperty("os.name").lowercase().contains("windows")
      if (isWindows) {
        ProcessBuilder("cmd.exe", "/c", command)
      } else {
        ProcessBuilder("sh", "-c", command)
      }
    } else {
      // Splitting the command by spaces is naive, but ok for simple cases
      ProcessBuilder(*command.split(" ").toTypedArray())
    }

    return processBuilder.start()
  }

  override suspend fun onExecutionSuccess(outputs: List<IOutputFile>, timing: ExecutionTiming) {
    logger.debug("[$uploadServiceType] Successfully uploaded ${outputs.size / 2} files in ${timing.duration}")
  }

  override suspend fun onItemSuccess(
    input: IOutputFile,
    outputs: List<IOutputFile>,
    timing: ItemExecutionTiming,
  ) {
    val remoteItem = outputs.find { it.path != input.path }
    logger.debug("[$uploadServiceType] Uploaded ${input.path} to ${remoteItem?.path ?: "remote location"} in ${timing.duration}")
  }
}
