package github.hua0512.data.upload

/**
 * @author hua0512
 * @date : 2024/2/11 23:16
 */
data class RcloneConfig(
  val remotePath: String,
  val args: List<String> = emptyList(),
) : UploadConfig(UploadPlatform.RCLONE)