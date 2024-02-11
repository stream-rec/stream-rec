package github.hua0512.data

import kotlinx.serialization.Serializable

@Serializable
data class StreamData(
  val id: Int? = null,
  val title: String,
  val dateStart: Long? = null,
  val dateEnd: Long? = null,
  val streamerId: Long,
  val outputFilePath: String,
  val danmuFilePath: String? = null,
)