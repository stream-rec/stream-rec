package github.hua0512.data.upload

import github.hua0512.data.dto.RcloneConfigDTO

/**
 * @author hua0512
 * @date : 2024/2/11 23:16
 */
data class RcloneConfig(
  override val rcloneOperation: String = "copy",
  override val remotePath: String,
  override val args: List<String> = emptyList(),
) : UploadConfig(UploadPlatform.RCLONE), RcloneConfigDTO