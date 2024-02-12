package github.hua0512.data.config

import github.hua0512.data.StreamData
import github.hua0512.data.VideoFormat
import github.hua0512.data.dto.HuyaConfigDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@SerialName("huya")
data class HuyaDownloadConfig(
  override val primaryCdn: String? = null,
  override val danmu: Boolean? = null,
  override val maxBitRate: Int? = null,
  override val outputFolder: String? = null,
  override val outputFileName: String? = null,
  override val outputFileExtension: VideoFormat? = null,
  @Transient
  override val onPartedDownload: (StreamData) -> Unit = {},
  @Transient
  override val onStreamingFinished: (List<StreamData>) -> Unit = {},
) : DownloadConfig(), HuyaConfigDTO {

  companion object {
    val default = HuyaDownloadConfig(
      primaryCdn = "AL",
      danmu = false,
      maxBitRate = 10000,
      outputFolder = "",
      outputFileExtension = VideoFormat.flv,
    )
  }

  override val cookies: String? = null
}

