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

package github.hua0512.plugins.douyu.download

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.DownloadConfig.DouyuDownloadConfig
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.douyu.danmu.DouyuDanmu
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.utils.warn

/**
 * Douyu live stream downloader.
 * @author hua0512
 * @date : 2024/3/23 0:06
 */
class Douyu(
  app: App,
  override val danmu: DouyuDanmu,
  override val extractor: DouyuExtractor,
) :
  PlatformDownloader<DouyuDownloadConfig>(app, danmu = danmu, extractor) {


  override suspend fun shouldDownload(onLive: () -> Unit): Result<Boolean, ExtractorError> {
    extractor.selectedCdn = (downloadConfig.cdn ?: app.config.douyuConfig.cdn)
    return super.shouldDownload {
      onLive()
      // bind rid to avoid second time extraction
      danmu.rid = extractor.rid
    }
  }

  override fun getProgramArgs(): List<String> = emptyList()

  override fun onConfigUpdated(config: AppConfig) {
    super.onConfigUpdated(config)
    extractor.selectedCdn = config.douyuConfig.cdn
  }

  override suspend fun <T : DownloadConfig> T.applyFilters(streams: List<StreamInfo>): Result<StreamInfo, ExtractorError> {
    this as DouyuDownloadConfig
    val selectedCdn = cdn ?: app.config.douyuConfig.cdn
    val selectedQuality = quality ?: app.config.douyuConfig.quality
    val group = streams.groupBy { it.extras["cdn"] }
    val cdnStreams = group[selectedCdn] ?: group.values.flatten().also { warn("CDN {} not found, using random", selectedCdn) }
    val qualityStreams = cdnStreams.firstOrNull { it.extras["rate"] == selectedQuality.rate.toString() } ?: cdnStreams.firstOrNull()
      .also { warn("{} quality not found, using first one available {}", selectedQuality, it) }
    return Ok(qualityStreams ?: streams.first())
  }
}