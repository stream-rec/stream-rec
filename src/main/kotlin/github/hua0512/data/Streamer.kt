package github.hua0512.data

import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.StreamerDTO
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class Streamer(
  override val name: String,
  override val url: String,
  @Transient
  override val platform: StreamingPlatform = StreamingPlatform.UNKNOWN,
  @Transient
  override var isLive: Boolean = false,
  override var isActivated: Boolean = true,
  override val downloadConfig: DownloadConfig? = null,
) : StreamerDTO {

}
