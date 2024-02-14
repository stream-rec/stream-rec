package github.hua0512.data.upload

/**
 * @author hua0512
 * @date : 2024/2/9 19:16
 */
data class UploadResult(
  val time: Long,
  val isSuccess: Boolean = false,
  val message: String = "",
  val filePath: String = "",
)