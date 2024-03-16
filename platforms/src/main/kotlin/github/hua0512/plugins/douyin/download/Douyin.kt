/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.plugins.douyin.download

import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig.DefaultDownloadConfig
import github.hua0512.data.config.DownloadConfig.DouyinDownloadConfig
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.platform.DouyinQuality
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.base.Download
import github.hua0512.plugins.douyin.danmu.DouyinDanmu
import github.hua0512.utils.withIOContext

/**
 * This class represents a Douyin downloader.
 *
 * @property app The [App] instance.
 * @property danmu The [DouyinDanmu] instance.
 * @property streamer The [Streamer] instance.
 * @property downloadTitle The title of the downloaded video.
 * @property downloadUrl The URL of the video to be downloaded.
 */
class Douyin(app: App, danmu: DouyinDanmu, extractor: DouyinExtractor) : Download(app, danmu, extractor) {


  override suspend fun shouldDownload(streamer: Streamer): Boolean {
    this.streamer = streamer

    val config: DouyinDownloadConfig = if (streamer.templateStreamer != null) {
      /**
       * template config uses basic config [DefaultDownloadConfig], build a new douyin config using global platform values
       */
      streamer.templateStreamer?.downloadConfig?.run {
        DouyinDownloadConfig(
          quality = app.config.douyinConfig.quality,
          cookies = app.config.douyinConfig.cookies
        ).also {
          it.danmu = this.danmu
          it.maxBitRate = this.maxBitRate
          it.outputFileFormat = this.outputFileFormat
          it.outputFileName = this.outputFileName
          it.outputFolder = this.outputFolder
          it.onPartedDownload = this.onPartedDownload ?: emptyList()
          it.onStreamingFinished = this.onStreamingFinished ?: emptyList()
        }
      } ?: throw IllegalArgumentException("${streamer.name} has template streamer but no download config") // should not happen
    } else {
      streamer.downloadConfig as? DouyinDownloadConfig ?: DouyinDownloadConfig()
    }
    val cookies = (config.cookies ?: app.config.douyinConfig.cookies)?.also {
      extractor.cookies = it
    }

    val mediaInfo: MediaInfo = try {
      withIOContext { extractor.extract() }
    } catch (e: Exception) {
      logger.error("Error while extracting douyin data", e)
      return false
    }

    if (mediaInfo.artistImageUrl != streamer.avatar) {
      streamer.avatar = mediaInfo.artistImageUrl
    }
    downloadTitle = mediaInfo.title
    if (mediaInfo.title != streamer.streamTitle) {
      streamer.streamTitle = mediaInfo.title
    }
    if (!mediaInfo.live) return false

    if (mediaInfo.streams.isEmpty()) {
      logger.info("${streamer.name} has no streams")
      return false
    }

    val selectedQuality = (config.quality?.value ?: app.config.douyinConfig.quality.value).run {
      this.ifEmpty { DouyinQuality.origin.value }
    }
    val selectedQualityStreams = mediaInfo.streams.filter { it.extras["sdkKey"] == selectedQuality }.run {
      ifEmpty { mediaInfo.streams }
    }
    val userSelectedSourceFormat = (config.sourceFormat ?: app.config.douyinConfig.sourceFormat) ?: VideoFormat.flv
    val selectedStream =
      selectedQualityStreams.firstOrNull { it.format == userSelectedSourceFormat } ?: selectedQualityStreams.maxBy { it.bitrate }.also {
        logger.warn("${streamer.name} selected source format $userSelectedSourceFormat is not available, choosing ${it.format} with bitrate ${it.bitrate}")
      }

    downloadUrl = selectedStream.url
    return true
  }

  companion object {
    private const val BASE_URL = "https://www.douyin.com"
  }
}