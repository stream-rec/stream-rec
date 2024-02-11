package github.hua0512.data.dto

import github.hua0512.data.StreamData
import github.hua0512.data.VideoFormat
import kotlinx.serialization.Transient

/**
 * @author hua0512
 * @date : 2024/2/11 19:58
 */
interface DownloadConfigDTO {

  abstract val cookies: String?
  abstract val danmu: Boolean
  abstract val maxBitRate: Int?
  abstract val outputFolder: String?
  abstract val outputFileName: String?
  abstract val outputFileExtension: VideoFormat?

  @Transient
  abstract val onPartedDownload: (StreamData) -> Unit

  @Transient
  abstract val onStreamingFinished: (List<StreamData>) -> Unit

}