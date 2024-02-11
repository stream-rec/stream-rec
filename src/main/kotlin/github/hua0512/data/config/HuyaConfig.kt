package github.hua0512.data.config

import github.hua0512.data.StreamData
import github.hua0512.data.VideoFormat
import github.hua0512.data.dto.HuyaConfigDTO
import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/11 13:28
 */
@Serializable
data class HuyaConfig(
  override val primaryCdn: String = "AL",
  override val maxBitRate: Int? = 10000,
  override val cookies: String? = null,
) : HuyaConfigDTO {
  override val danmu: Boolean
    get() = TODO("Not yet implemented")
  override val outputFolder: String?
    get() = TODO("Not yet implemented")
  override val outputFileName: String?
    get() = TODO("Not yet implemented")
  override val outputFileExtension: VideoFormat?
    get() = TODO("Not yet implemented")
  override val onPartedDownload: (StreamData) -> Unit
    get() = TODO("Not yet implemented")
  override val onStreamingFinished: (List<StreamData>) -> Unit
    get() = TODO("Not yet implemented")
}