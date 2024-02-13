package github.hua0512.data.entity

import github.hua0512.data.StreamingPlatform
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.StreamerDTO
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * @author hua0512
 * @date : 2024/2/10 19:56
 */
@ExperimentalSerializationApi
data class StreamerEntity(
  override val name: String,
  override val url: String,
  override val platform: StreamingPlatform,
  override var isLive: Boolean = false,
  override var isActivated: Boolean = true,
  override val downloadConfig: DownloadConfig? = null,
) : StreamerDTO

