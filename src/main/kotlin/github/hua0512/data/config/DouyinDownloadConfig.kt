package github.hua0512.data.config

import github.hua0512.data.StreamData
import github.hua0512.data.VideoFormat
import github.hua0512.data.platform.DouyinQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/9 1:25
 */
@Serializable
@SerialName("douyin")
data class DouyinDownloadConfig(
  override var cookies: String? = null,
  override val danmu: Boolean? = null,
  val quality: DouyinQuality? = null,
) : DownloadConfig() {

  override var maxBitRate: Int? = null
  override var outputFolder: String? = null
  override val outputFileName: String? = null
  override var outputFileExtension: VideoFormat? = null
  override var onPartedDownload: (StreamData) -> Unit = {}
  override var onStreamingFinished: (List<StreamData>) -> Unit = {}

  companion object {
    val default = DouyinDownloadConfig(
      cookies = null,
      danmu = false,
      quality = DouyinQuality.origin,
    ).also {
      it.maxBitRate = 10000
      it.outputFolder = ""
      it.outputFileExtension = VideoFormat.flv
    }
  }

}