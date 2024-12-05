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

package github.hua0512.plugins.pandatv.download

import com.github.michaelbull.result.Result
import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig.PandaTvDownloadConfig
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.download.base.HlsPlatformDownloader
import github.hua0512.plugins.pandatv.danmu.PandaTvDanmu

/**
 * Pandalive live stream downloader.
 * @author hua0512
 * @date : 2024/5/10 13:22
 */
class PandaTv(
  app: App,
  override val danmu: PandaTvDanmu,
  override val extractor: PandaTvExtractor,
) :
  HlsPlatformDownloader<PandaTvDownloadConfig>(app, danmu, extractor) {

  override suspend fun shouldDownload(onLive: () -> Unit): Result<Boolean, ExtractorError> = super.shouldDownload {
    onLive()
    // init danmu
    danmu.apply {
      userIdx = extractor.userIdx
      token = extractor.token
    }
  }

  override fun getPlatformHeaders(): Map<String, String> = extractor.getRequestHeaders()

  override fun getProgramArgs(): List<String> = emptyList()

  override fun onConfigUpdated(config: AppConfig) {
    super.onConfigUpdated(config)
  }
}