package github.hua0512.data.config

import github.hua0512.data.Streamer
import github.hua0512.data.VideoFormat
import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/2/11 13:19
 */
@Serializable
data class AppConfig(
  val engine: String = "ffmpeg",
  val danmu: Boolean = false,
  val outputFolder: String = "",
  val outputFileName: String = "\${stremerName}-\${title}-%yyyy-%MM-%dd %HH:%mm:%ss",
  val outputFileFormat: VideoFormat = VideoFormat.flv,
  val minPartSize: Long = 52428800,
  val maxPartSize: Long = 2621440000,
  val maxDownloadRetries: Int = 3,
  val downloadRetryDelay: Long = 10,
  val maxConcurrentDownloads: Int = 5,
  val maxConcurrentUploads: Int = 3,

  val huyaConfig: HuyaConfig = HuyaConfig(),
  val douyinConfig: DouyinConfig = DouyinConfig(),

  val streamers: List<Streamer> = emptyList(),
)