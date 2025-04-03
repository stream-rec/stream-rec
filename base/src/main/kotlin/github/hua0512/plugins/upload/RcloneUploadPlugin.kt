package github.hua0512.plugins.upload

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginConfigs.UploadConfig.RcloneConfig
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
 * Upload plugin that uses rclone to upload files to various cloud storage services.
 * Supports Google Drive, Dropbox, S3, OneDrive and many others supported by rclone.
 */
class RcloneUploadPlugin(
  config: RcloneConfig,
  uploadSemaphore: Semaphore,
  streamRepo: StreamDataRepo,
  uploadRepository: UploadRepo,
) : BaseUploadPlugin<RcloneConfig>(config, uploadSemaphore, streamRepo, uploadRepository) {

  override val id: String = "5f8fd45c-0692-42c2-aa39-77b1825301ca"
  override val name: String = "Rclone Upload"
  override val description: String = "Uploads files using rclone to various cloud storage providers"
  override val version: String = "1.0.0"
  override val author: String = "System"
  override val uploadServiceType: String = "rclone"

  override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
    try {
      // Execute 'rclone lsd remote:' to test connection
      val rclonePath = config.rclonePath ?: "rclone"
      val command = mutableListOf(rclonePath, "lsd")

      addConfigFileIfSpecified(command)

      command.add(config.remoteName)

      val process = ProcessBuilder(command)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

      val exitCode = process.waitFor()
      return@withContext exitCode == 0
    } catch (e: Exception) {
      return@withContext false
    }
  }

  override suspend fun authenticate(): Boolean = true // Authentication is handled by rclone config

  override suspend fun performUpload(file: IOutputFile): Result<UploadPluginResult, UploadError> =
    withContext(Dispatchers.IO) {
      val localFile = File(file.path)
      if (!localFile.exists()) {
        return@withContext Err(UploadError.ValidationError("File not found: ${file.path}"))
      }

      try {
        val destPath = buildRemotePath(localFile, file)
        val command = buildRcloneCommand(localFile, destPath)

        val startTime = System.currentTimeMillis()

        val process = ProcessBuilder(command)
          .redirectOutput(ProcessBuilder.Redirect.PIPE)
          .redirectError(ProcessBuilder.Redirect.PIPE)
          .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }

        val exitCode = process.waitFor()
        val endTime = System.currentTimeMillis()

        if (exitCode != 0) {
          return@withContext Err(
            UploadError.TransferError(
              message = "Rclone upload failed with exit code $exitCode",
              statusCode = exitCode,
              response = stderr
            )
          )
        }

        // Generate URL if public link creation is enabled
        val url = if (config.createPublicLinks) {
          try {
            createPublicLink(destPath)
          } catch (e: Exception) {
            null
          }
        } else null

        val result = UploadPluginResult(
          success = true,
          url = url,
          remoteId = destPath,
          size = localFile.length(),
          uploadTimeMs = endTime - startTime,
          metadata = mapOf(
            "command" to command.joinToString(" "),
            "stdout" to stdout,
            "stderr" to stderr,
            "remotePath" to destPath
          )
        )

        // If transfer mode is 'move', rclone deletes source file after upload
        // We should respect the plugin's deleteAfterUpload setting regardless
        if (config.transferMode == "move" && !config.deleteAfterUpload) {
          // Log that the source was deleted by rclone
          println("File ${localFile.path} was deleted by rclone due to move mode.")
        }

        Ok(result)
      } catch (e: Exception) {
        Err(UploadError.UnknownError("Error during rclone upload: ${e.message}", e))
      }
    }

  /**
   * Builds the full destination path on the remote.
   */
  private fun buildRemotePath(localFile: File, input: IOutputFile): String {
    var path = config.remotePath.replacePlaceholders(input)

    // Ensure path has a trailing slash
    if (!path.endsWith("/")) {
      path += "/"
    }

    // Add filename
    path += localFile.name

    // Construct full remote path
    return "${config.remoteName}${path}"
  }

  /**
   * Builds the rclone command with appropriate flags.
   */
  private fun buildRcloneCommand(localFile: File, remotePath: String): List<String> {
    val rclonePath = config.rclonePath ?: "rclone"
    val command = mutableListOf<String>()

    command.add(rclonePath)

    // Add transfer mode (copy/move/sync)
    command.add(config.transferMode)

    // Add config file if specified
    addConfigFileIfSpecified(command)

    // Add options
    if (config.checksum) {
      command.add("--checksum")
    }

    config.bufferSize?.let {
      command.add("--buffer-size")
      command.add(it)
    }

    config.transfers?.let {
      command.add("--transfers")
      command.add(it.toString())
    }

    config.bandwidthLimit?.let {
      command.add("--bwlimit")
      command.add(it)
    }

    // Add progress flag for better logging
    command.add("-P")

    // Add additional custom flags
    command.addAll(config.additionalFlags)

    // Add source and destination
    command.add(localFile.absolutePath)
    command.add(remotePath)

    return command
  }

  /**
   * Adds config file parameter if specified in configuration.
   */
  private fun addConfigFileIfSpecified(command: MutableList<String>) {
    config.configFile?.let {
      command.add("--config")
      command.add(it)
    }
  }

  /**
   * Creates a public link for the uploaded file if supported by the provider.
   */
  private suspend fun createPublicLink(remotePath: String): URL? = withContext(Dispatchers.IO) {
    try {
      val rclonePath = config.rclonePath ?: "rclone"
      val command = mutableListOf(rclonePath, "link")

      addConfigFileIfSpecified(command)

      command.add(remotePath)

      val process = ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

      val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
      val exitCode = process.waitFor()

      if (exitCode == 0 && output.isNotBlank() && (output.startsWith("http://") || output.startsWith("https://"))) {
        return@withContext URL(output)
      }

      return@withContext null
    } catch (e: Exception) {
      return@withContext null
    }
  }

  override suspend fun validateUpload(
    localFile: IOutputFile,
    uploadResult: UploadPluginResult,
  ): Result<Unit, UploadError> = withContext(Dispatchers.IO) {
    if (!config.validateAfterUpload) {
      return@withContext Ok(Unit)
    }

    try {
      // Use rclone check to validate upload if checksum is enabled
      if (config.checksum) {
        val rclonePath = config.rclonePath ?: "rclone"
        val remotePath = uploadResult.remoteId ?: return@withContext Err(
          UploadError.ValidationError("Missing remote path in upload result")
        )

        val command = mutableListOf(rclonePath, "check")

        addConfigFileIfSpecified(command)

        // Add options
        command.add("--one-way") // Only check that source exists at destination

        // Add source and destination
        command.add(localFile.path)
        command.add(remotePath)

        val process = ProcessBuilder(command)
          .redirectOutput(ProcessBuilder.Redirect.PIPE)
          .redirectError(ProcessBuilder.Redirect.PIPE)
          .start()

        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
          return@withContext Err(
            UploadError.ValidationError(
              "Failed to validate upload with rclone check: $stderr"
            )
          )
        }
      }

      return@withContext Ok(Unit)
    } catch (e: Exception) {
      return@withContext Err(
        UploadError.ValidationError(
          "Exception during validation: ${e.message}", e
        )
      )
    }
  }

  /**
   * Provide detailed upload information including transfer statistics.
   */
  override suspend fun onItemSuccess(
    input: IOutputFile,
    outputs: List<IOutputFile>,
    timing: ItemExecutionTiming,
  ) {
    val remoteItem = outputs.find { it.path != input.path }
    val uploadSpeed = if (timing.duration.inWholeMilliseconds > 0) {
      val bytesPerMs = input.size.toDouble() / timing.duration.inWholeMilliseconds
      val mbps = bytesPerMs * 8 / 1000 // Convert to Mbps
      String.format("%.2f Mbps", mbps)
    } else "∞ Mbps"

    // Include information about the remote used
    val remoteName = config.remoteName.removeSuffix(":")
    println(
      "[$uploadServiceType:$remoteName] Uploaded ${input.path} (${formatFileSize(input.size)}) to " +
              "${remoteItem?.path ?: "remote location"} in ${timing.duration} ($uploadSpeed)"
    )
  }

  /**
   * Format file size in human readable format.
   */
  private fun formatFileSize(size: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var unitIndex = 0

    while (value >= 1024 && unitIndex < units.size - 1) {
      value /= 1024
      unitIndex++
    }

    return String.format("%.2f %s", value, units[unitIndex])
  }
}
