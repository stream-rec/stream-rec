package github.hua0512.data.dto

import github.hua0512.data.StreamingPlatform
import github.hua0512.data.config.DownloadConfig

/**
 * @author hua0512
 * @date : 2024/2/10 19:54
 */
interface StreamerDTO {
  val name: String
  val url: String
  val platform: StreamingPlatform
  var isLive: Boolean
  var isActivated: Boolean
  val downloadConfig: DownloadConfig?
}
