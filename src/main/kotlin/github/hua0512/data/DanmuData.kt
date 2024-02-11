package github.hua0512.data

import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/11 1:21
 */
@Serializable
data class DanmuData(
  val sender: String,
  val color: Int,
  val content: String,
  val fontSize: Int,
  val time: Double,
)