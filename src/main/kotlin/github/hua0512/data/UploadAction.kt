package github.hua0512.data

import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/9 19:07
 */
@Serializable
data class UploadAction(
  val id: Long,
  val time: Long,
  val uploadDataList: List<UploadData> = emptyList(),
)