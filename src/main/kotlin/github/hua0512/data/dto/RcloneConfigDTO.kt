package github.hua0512.data.dto

/**
 * @author hua0512
 * @date : 2024/2/13 13:26
 */
interface RcloneConfigDTO {
  val rcloneOperation: String
  val remotePath: String
  val args: List<String>
}