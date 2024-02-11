package github.hua0512.data.config

import github.hua0512.data.StreamData
import github.hua0512.data.VideoFormat
import github.hua0512.data.dto.DouyinConfigDTO
import github.hua0512.data.platform.DouyinQuality
import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/11 13:29
 */
@Serializable
data class DouyinConfig(
  override val cookies: String? = null,
  override val quality: DouyinQuality = DouyinQuality.origin,
) : DouyinConfigDTO {
  override val danmu: Boolean
    get() = TODO("Not yet implemented")
  override val maxBitRate: Int?
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