package github.hua0512.data

import kotlinx.serialization.Serializable

@Serializable
data class UploadData(
  val id: Long? = null,
  val streamerId: Long,
  val streamDataId: Long,
  val isSuccess: Boolean,
  val streamData: StreamData,
  val platform: UploadPlatform,
)
