package github.hua0512.data.config

import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/13 13:26
 */
@Serializable
sealed class Action {
  val enabled: Boolean = true
}