package github.hua0512.data.config

import github.hua0512.data.dto.RcloneConfigDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/13 13:26
 */
@Serializable
@SerialName("rclone")
data class RcloneAction(
  override val rcloneOperation: String,
  override val remotePath: String,
  override val args: List<String>,
) : Action(), RcloneConfigDTO
