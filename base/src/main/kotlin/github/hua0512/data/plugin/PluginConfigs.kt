@file:OptIn(ExperimentalSerializationApi::class)

package github.hua0512.data.plugin

import github.hua0512.app.Programs
import github.hua0512.data.dto.RcloneConfigDTO
import github.hua0512.data.upload.UploadPlatform
import github.hua0512.plugins.command.CommandConfig
import github.hua0512.plugins.ffmpeg.FFmpegConfig
import github.hua0512.plugins.file.FileOperationConfig
import io.ktor.http.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.net.URL
import kotlin.time.Duration.Companion.hours

/**
 * Sealed class for actions that can be performed after a stream is downloaded
 * @author hua0512
 * @date : 2024/2/13 13:26
 */
@Serializable
sealed class PluginConfigs {

  @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
  var enabled: Boolean = true


  /**
   * Configuration for a simple shell command plugin.
   */
  @Serializable
  @SerialName("command")
  data class SimpleShellCommandConfig(
    /**
     * The command template to execute.
     * Can include placeholders like {input} which will be replaced with input file paths.
     */
    val commandTemplate: String,

    /**
     * Whether to use shell expansion for the command.
     * If true, the command will be executed in a shell (cmd.exe or /bin/sh).
     */
    val useShell: Boolean = false,

    /**
     * Arguments to pass to the command.
     */
    @JsonNames("arguments", "args")
    val arguments: List<String> = emptyList(),

    /**
     * Include input file path as an argument.
     */
    val includeInputPath: Boolean = true,

    /**
     * Whether to include each input file as a separate argument.
     */
    val includeAllInputs: Boolean = false,

    override val timeoutMs: Long = 0,
    override val workingDirectory: String? = null,
    override val environmentVariables: Map<String, String> = emptyMap(),
    override val redirectOutput: Boolean = true,
    override val redirectError: Boolean = true,
    override val fileFilter: String? = null,
    override val retryCount: Int = 0,
    override val retryDelayMs: Long = 0,
    override val maxOutputSize: Int = 10_000,
    override val failOnNonZeroExit: Boolean = true,
    override val allowedCommands: List<String> = emptyList(),
  ) : PluginConfigs(), CommandConfig


  @Serializable
  @SerialName("delete")
  /**
   * Configuration for file deletion plugin.
   */
  data class DeleteFileConfig(
    /**
     * If true, files will be securely deleted (overwritten before deletion).
     * This is a more secure but slower operation.
     */
    val secureDelete: Boolean = false,

    /**
     * Number of secure deletion passes (only used if secureDelete is true).
     * More passes means more secure but slower deletion.
     */
    val secureDeletionPasses: Int = 3,

    /**
     * If true, files will be moved to system trash/recycle bin instead of permanently deleted.
     * This takes precedence over secureDelete if both are true.
     */
    val moveToTrash: Boolean = false,

    /**
     * Age threshold in milliseconds - files older than this will be deleted.
     * If zero, all matching files will be deleted regardless of age.
     */
    val olderThanMs: Long = 0,

    override val operationTimeoutMs: Long = 0,
    override val retryCount: Int = 0,
    override val retryDelayMs: Long = 0,
    override val createDirectories: Boolean = false, // Not used for delete
    override val preserveAttributes: Boolean = false, // Not used for delete
    override val overwriteExisting: Boolean = false, // Not used for delete
    override val fileFilter: String? = null,
  ) : PluginConfigs(), FileOperationConfig


  @Serializable
  @SerialName("move")
  /**
   * Configuration for file move plugin.
   */
  data class MoveFileConfig(
    /**
     * Destination directory where files will be moved.
     */
    val destinationDirectory: String,
    override val overwriteExisting: Boolean = false,
    override val operationTimeoutMs: Long = 0,
    override val retryCount: Int = 0,
    override val retryDelayMs: Long = 0,
    override val createDirectories: Boolean = true,
    override val preserveAttributes: Boolean = true,
    override val fileFilter: String? = null,
  ) : PluginConfigs(), FileOperationConfig


  /**
   * Configuration for file copy plugin.
   */
  @Serializable
  @SerialName("copy")
  data class CopyFileConfig(
    /**
     * Destination directory where files will be copied.
     */
    val destinationDirectory: String,
    override val overwriteExisting: Boolean = false,
    override val operationTimeoutMs: Long = 0,
    override val retryCount: Int = 0,
    override val retryDelayMs: Long = 0,
    override val createDirectories: Boolean = true,
    override val preserveAttributes: Boolean = true,
    override val fileFilter: String? = null,
  ) : PluginConfigs(), FileOperationConfig


  /**
   * Configuration for the remuxing plugin.
   */
  @Serializable
  @SerialName("remux")
  data class RemuxConfig(
    /**
     * Output container format (e.g., "mp4", "mkv", "ts").
     */
    val outputFormat: String,

    /**
     * Whether to copy all streams without re-encoding.
     */
    val copyAllStreams: Boolean = true,

    /**
     * Specific streams to include (e.g., "v:0,a:0" for first video and audio).
     * If null, all streams are included.
     */
    val mapStreams: String? = null,

    /**
     * Additional format options for muxing.
     * Example: ["movflags=faststart", "frag_keyframe"]
     */
    val formatOptions: List<String> = emptyList(),

    /**
     * Whether to preserve original timestamps.
     */
    val preserveTimestamps: Boolean = true,

    /**
     * Whether to fix broken streams or timestamps.
     */
    val fixBrokenStreams: Boolean = true,

    /**
     * Maximum muxing queue size for FFmpeg.
     */
    val maxMuxingQueueSize: Int? = null,

    /**
     * Whether to delete original files after remuxing.
     */
    val deleteOriginalFilesAfterRemux: Boolean = false,
    /**
     * Whether to force delete original files if remuxing contains errors or warnings.
     */
    val forceDeleteIfErrorInRemux: Boolean = false,

    override val ffmpegPath: String? = Programs.ffmpeg,
    override val globalOptions: List<String> = emptyList(),
    override val overwrite: Boolean = false,
    override val logLevel: String = "info",
    override val metadata: Map<String, String> = emptyMap(),
    override val outputExtension: String? = null,
    override val outputDirectory: String? = null,

    override val timeoutMs: Long = 0,
    override val workingDirectory: String? = null,
    override val environmentVariables: Map<String, String> = emptyMap(),
    override val redirectOutput: Boolean = true,
    override val redirectError: Boolean = true,
    override val retryCount: Int = 0,
    override val retryDelayMs: Long = 0,
    override val maxOutputSize: Int = 10000,
    override val failOnNonZeroExit: Boolean = true,
    override val allowedCommands: List<String> = listOf("ffmpeg"),
  ) : PluginConfigs(), FFmpegConfig {

    // Accept common video files only
    override val fileFilter: String =
      ".*\\.mp4|.*\\.mkv|.*\\.avi|.*\\.mov|.*\\.wmv|.*\\.flv|.*\\.webm|.*\\.ts|.*\\.m2ts"
  }

  @Serializable
  @SerialName("api")
  data class ApiPluginConfig(
    val name: String = "API",
    val baseUrl: String,
    val auth: ApiAuth? = null,
    val timeoutMs: Long = 30000,
    val endpoints: List<ApiEndpoint> = emptyList(),
  ) : PluginConfigs()

  @Serializable
  data class ApiEndpoint(
    val name: String,
    val path: String,
    val method: String = "POST",
    val contentType: String? = null,
    val formFieldName: String = "file",
    val additionalHeaders: Map<String, String> = emptyMap(),
    val additionalFields: Map<String, String> = emptyMap(),
  )


  @Serializable
  sealed class UploadConfig(
    override val platform: UploadPlatform = UploadPlatform.NONE,
  ) : PluginConfigs(), IUploadConfig {


    /**
     * Configuration for Rclone upload plugin.
     */
    @Serializable
    @SerialName("rclone")
    data class RcloneConfig(
      /**
       * The remote name as configured in rclone.
       * Example: "gdrive:", "s3:", "dropbox:"
       */
      val remoteName: String = "",

      /**
       * Base path on the remote where files will be uploaded.
       */
      override val remotePath: String = "/",

      /**
       * Path to the rclone executable.
       * If null, assumes rclone is in the system PATH.
       */
      val rclonePath: String? = null,

      /**
       * Optional configuration file path.
       * If provided, --config parameter will be added to rclone commands.
       */
      val configFile: String? = null,

      /**
       * Additional flags to pass to rclone.
       */
      @JsonNames("additionalFlags", "args")
      override val additionalFlags: List<String> = emptyList(),

      /**
       * If true, creates public links for uploads if supported by the provider.
       */
      val createPublicLinks: Boolean = false,

      /**
       * Transfer mode for rclone.
       * - "copy": Regular copy
       * - "move": Move files (delete source after successful upload)
       * - "sync": Sync source to destination
       */
      @JsonNames("transferMode", "rcloneOperation")
      override val transferMode: String = "copy",

      /**
       * Buffer size for transfers. Example: "8M", "16M"
       */
      val bufferSize: String? = null,

      /**
       * Number of transfer threads (rclone --transfers parameter)
       */
      val transfers: Int? = null,

      /**
       * Bandwidth limit. Example: "10M", "1G"
       */
      val bandwidthLimit: String? = null,

      /**
       * If true, enables rclone's checksum verification
       */
      val checksum: Boolean = false,
      override val timeoutMs: Long = 0,
      override val retryCount: Int = 0,
      override val retryDelayMs: Long = 0,
      override val fileFilter: String? = null,
      override val deleteAfterUpload: Boolean = false,
      override val validateAfterUpload: Boolean = false,
    ) : UploadConfig(UploadPlatform.RCLONE), RcloneConfigDTO

    /**
     * Configuration for API-based upload plugins.
     */
    @Serializable
    data class ApiUploadConfig(
      /**
       * Base URL of the API.
       */
      val baseUrl: String,

      /**
       * Path for the upload endpoint.
       */
      val uploadPath: String,

      /**
       * Path for the authentication endpoint, if needed.
       */
      val authPath: String? = null,

      /**
       * Method for upload requests (e.g., POST, PUT).
       */
      val uploadMethod: String = HttpMethod.Post.value,

      /**
       * Additional headers for API requests.
       */
      val headers: Map<String, String> = emptyMap(),

      /**
       * Authentication credentials.
       */
      val auth: ApiAuth? = null,

      /**
       * Field name for the file in multipart form uploads.
       */
      val fileFieldName: String = "file",

      /**
       * Additional form fields to include with the upload.
       */
      val additionalFields: Map<String, String> = emptyMap(),

      /**
       * JSON path to extract URL from server response (e.g., "data.url").
       */
      val urlJsonPath: String? = null,

      /**
       * JSON path to extract file ID from server response.
       */
      val fileIdJsonPath: String? = null,
      override val timeoutMs: Long = 6.hours.inWholeMilliseconds, // 5 minutes
      override val retryCount: Int = 3,
      override val retryDelayMs: Long = 5000L, // 5 seconds
      override val fileFilter: String? = null,
      override val deleteAfterUpload: Boolean = false,
      override val validateAfterUpload: Boolean = true,
    ) : UploadConfig(UploadPlatform.EXTERNAL)


    /**
     * Configuration for command-based upload plugins.
     */
    @Serializable
    data class CommandUploadConfig(
      /**
       * The command template to execute for uploads.
       * Can include placeholders like {file} which will be replaced with the file path.
       */
      val uploadCommandTemplate: String,

      /**
       * Optional command to test the connection.
       */
      val testConnectionCommand: String? = null,

      /**
       * Optional command for authentication.
       */
      val authCommand: String? = null,

      /**
       * Pattern to extract the URL from command output.
       * This should be a regex with a capturing group for the URL.
       */
      val urlExtractionPattern: String? = null,

      /**
       * Pattern to extract the remote ID from command output.
       */
      val remoteIdExtractionPattern: String? = null,

      /**
       * Whether to use shell execution for the command.
       */
      val useShell: Boolean = false,

      /**
       * Additional arguments for the upload command.
       */
      val uploadCommandArgs: List<String> = emptyList(),

      override val timeoutMs: Long = 300000L, // 5 minutes
      override val retryCount: Int = 3,
      override val retryDelayMs: Long = 5000L, // 5 seconds
      override val fileFilter: String? = null,
      override val deleteAfterUpload: Boolean = false,
      override val validateAfterUpload: Boolean = true,
    ) : UploadConfig(UploadPlatform.EXTERNAL)


  }

}