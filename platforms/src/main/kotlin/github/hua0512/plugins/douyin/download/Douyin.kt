/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig.DouyinDownloadConfig
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.platform.DouyinQuality
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.douyin.danmu.DouyinDanmu
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.utils.info

/**
 * This class represents a Douyin downloader.
 *
 * @property app The [App] instance.
 * @property danmu The [DouyinDanmu] instance.
 * @property extractor The [DouyinCombinedApiExtractor] instance.
 */
class Douyin(
  app: App,
  override val danmu: DouyinDanmu,
  override val extractor: DouyinStrevExtractor,
) :
  PlatformDownloader<DouyinDownloadConfig>(app, danmu, extractor) {

  override suspend fun shouldDownload(onLive: () -> Unit): Result<Boolean, ExtractorError> = super.shouldDownload {
    onLive()
    // bind idStr to danmu
    danmu.idStr = extractor.idStr
  }

  override fun getProgramArgs(): List<String> = emptyList()

  override fun onConfigUpdated(config: AppConfig) {
    super.onConfigUpdated(config)
  }

  override suspend fun applyFilters(streams: List<StreamInfo>): Result<StreamInfo, ExtractorError> {
    val config = downloadConfig
    val selectedQuality = (config.quality?.value ?: app.config.douyinConfig.quality.value).ifEmpty { DouyinQuality.origin.value }

    val selectedQualityStreams = streams.filter { it.extras["sdkKey"] == selectedQuality }.ifEmpty { streams }

    val userSelectedSourceFormat = (config.sourceFormat ?: app.config.douyinConfig.sourceFormat) ?: VideoFormat.flv

    val selectedFormatStreams = selectedQualityStreams.filter { it.format == userSelectedSourceFormat }.run {
      ifEmpty { selectedQualityStreams }
    }

    // prioritize flv format if user selected format is not available
    return Ok(selectedFormatStreams.maxByOrNull { it.bitrate } ?: selectedFormatStreams.filter { it.format == VideoFormat.flv }.maxBy { it.bitrate }
      .also {
        info("No stream with {} format, using default flv, bitrate: {}", userSelectedSourceFormat, it.bitrate)
      }
    )
  }
}