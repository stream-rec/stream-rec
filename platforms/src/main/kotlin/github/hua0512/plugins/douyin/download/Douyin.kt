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
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.DownloadConfig.DefaultDownloadConfig
import github.hua0512.data.config.DownloadConfig.DouyinDownloadConfig
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.platform.DouyinQuality
import github.hua0512.data.stream.StreamInfo
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

  private val config by lazy {
    if (streamer.templateStreamer != null) {
      /**
       * template config uses basic config [DefaultDownloadConfig], build a new douyin config using global platform values
       */
      streamer.templateStreamer?.downloadConfig?.run {
        DouyinDownloadConfig(
          quality = app.config.douyinConfig.quality,
          cookies = app.config.douyinConfig.cookies,
          sourceFormat = app.config.douyinConfig.sourceFormat
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
  }

  override suspend fun shouldDownload(streamer: Streamer): Boolean {
    this.streamer = streamer

    (config.cookies ?: app.config.douyinConfig.cookies)?.also {
      extractor.cookies = it
    }

    val mediaInfo: MediaInfo = try {
      withIOContext { extractor.extract() }
    } catch (e: Exception) {
      logger.error("Error while extracting douyin data", e)
      return false
    }

    return getStreamInfo(mediaInfo, streamer, config)
  }

  override suspend fun <T : DownloadConfig> T.applyFilters(streams: List<StreamInfo>): StreamInfo {
    this as DouyinDownloadConfig

    val selectedQuality = (quality?.value ?: app.config.douyinConfig.quality.value).run {
      this.ifEmpty { DouyinQuality.origin.value }
    }
    val selectedQualityStreams = streams.filter { it.extras["sdkKey"] == selectedQuality }.run {
      ifEmpty { streams }
    }
    val userSelectedSourceFormat = (sourceFormat ?: app.config.douyinConfig.sourceFormat) ?: VideoFormat.flv
    // prioritize flv format if user selected format is not available
    return selectedQualityStreams.firstOrNull { it.format == userSelectedSourceFormat }
      ?: selectedQualityStreams.filter { it.format == VideoFormat.flv }
        .maxBy { it.bitrate }.also {
          logger.info("No stream with format $userSelectedSourceFormat, using flv stream, bitrate: ${it.bitrate}")
        }
  }
}