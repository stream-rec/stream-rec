package github.hua0512.data.dto

import github.hua0512.data.StreamData
import github.hua0512.data.VideoFormat
import kotlinx.serialization.Transient

/**
 * @author hua0512
 * @date : 2024/2/11 19:58
 */
interface DownloadConfigDTO {

  val cookies: String?
  val danmu: Boolean
  val maxBitRate: Int?
  val outputFolder: String?
  val outputFileName: String?
  val outputFileExtension: VideoFormat?

  @Transient
  val onPartedDownload: (StreamData) -> Unit

  @Transient
  val onStreamingFinished: (List<StreamData>) -> Unit

}