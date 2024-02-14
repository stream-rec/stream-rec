package github.hua0512.data.upload

import kotlinx.serialization.Serializable

@Serializable
data class UploadData(
  val id: Long? = null,
  val streamTitle: String,
  val streamer: String,
  val streamStartTime: Long,
  val filePath: String,
)
