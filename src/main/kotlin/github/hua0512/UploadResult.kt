package github.hua0512

/**
 * @author hua0512
 * @date : 2024/2/9 19:16
 */
data class UploadResult(
  val id: Long,
  val time: Long,
  val uploadDataId: Long,
  val isSuccess: Boolean = false,
  val message: String = "",
)