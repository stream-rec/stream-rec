package github.hua0512.data.config

import github.hua0512.data.VideoFormat
import github.hua0512.data.dto.DownloadConfigDTO
import kotlinx.serialization.Serializable

@Serializable
sealed class DownloadConfig : DownloadConfigDTO {
  abstract override val cookies: String?
  abstract override val danmu: Boolean?
  abstract override val maxBitRate: Int?
  abstract override val outputFolder: String?
  abstract override val outputFileName: String?
  abstract override val outputFileExtension: VideoFormat?
  abstract override val onPartedDownload: List<Action>?
  abstract override val onStreamingFinished: List<Action>?
}