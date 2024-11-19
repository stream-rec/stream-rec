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

package github.hua0512.plugins.weibo.download

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.DownloadConfig.WeiboDownloadConfig
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.utils.warn

/**
 * Weibo platform downloader.
 * @author hua0512
 * @date : 2024/10/20 0:04
 */
class Weibo(app: App, danmu: Danmu, override val extractor: WeiboExtractor) :
  PlatformDownloader<WeiboDownloadConfig>(app, danmu, extractor) {

  override fun getPlatformHeaders(): Map<String, String> = extractor.getRequestHeaders()

  override fun getProgramArgs(): List<String> = emptyList()

  override fun onConfigUpdated(config: AppConfig) {
    super.onConfigUpdated(config)
  }

  override suspend fun <T : DownloadConfig> T.applyFilters(streams: List<StreamInfo>): Result<StreamInfo, ExtractorError> {
    val selectedStreamFormat = downloadConfig.sourceFormat ?: VideoFormat.flv
    val filtered = streams.firstOrNull { it.format == selectedStreamFormat } ?: streams.first().also {
      warn("No stream found for format {}, using {} instead", selectedStreamFormat, it.format)
    }
    return Ok(filtered)
  }
}