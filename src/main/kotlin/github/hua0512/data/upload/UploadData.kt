package github.hua0512.data.upload

import github.hua0512.data.StreamData
import kotlinx.serialization.Serializable

@Serializable
data class UploadData(
  val id: Long? = null,
  val streamerId: Long,
  val isSuccess: Boolean,
  val streamData: StreamData
)
