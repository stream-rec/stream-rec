package github.hua0512.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/13 13:52
 */
@Serializable
@SerialName("command")
data class CommandAction(
  val program: String,
  val args: List<String> = emptyList(),
) : Action()