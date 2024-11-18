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

package github.hua0512.plugins.huya.download

import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.DownloadConfig.HuyaDownloadConfig
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.plugins.huya.danmu.HuyaDanmu
import github.hua0512.utils.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Huya(
  app: App,
  override val danmu: HuyaDanmu,
  override val extractor: HuyaExtractor,
) :
  PlatformDownloader<HuyaDownloadConfig>(app, danmu, extractor) {

  init {
    onConfigUpdated(app.config)
  }


  override suspend fun shouldDownload(onLive: () -> Unit): Boolean = super.shouldDownload {
    onLive()
    // bind danmu properties
    with(danmu) {
      presenterUid = extractor.presenterUid
    }
  }

  override fun getPlatformHeaders(): Map<String, String> = extractor.getRequestHeaders()

  override fun getProgramArgs(): List<String> = emptyList()

  override fun onConfigUpdated(config: AppConfig) {
    super.onConfigUpdated(config)
    extractor.forceOrigin = config.huyaConfig.forceOrigin
  }

  override suspend fun <T : DownloadConfig> T.applyFilters(streams: List<StreamInfo>): StreamInfo {
    this as HuyaDownloadConfig
    // user defined source format
    val userPreferredFormat = (sourceFormat ?: app.config.huyaConfig.sourceFormat).apply {
      if (this !in streams.map { it.format }) {
        info("defined source format {} is not available, choosing the best available", this)
      }
    }
    // user defined max bit rate
    val maxUserBitRate = (maxBitRate ?: app.config.huyaConfig.maxBitRate) ?: 10000
    // user selected cdn
    var preselectedCdn = primaryCdn ?: app.config.huyaConfig.primaryCdn
    preselectedCdn = preselectedCdn.uppercase()

    // drop all streams with bit rate higher than user defined max bit rate
    val selectedCdnStreams = withContext(Dispatchers.Default) {
      streams.filter {
        it.bitrate <= maxUserBitRate
      }.groupBy {
        it.extras["cdn"]
      }.run {
        @Suppress("UNCHECKED_CAST")
        this as Map<String, List<StreamInfo>>

        if (preselectedCdn !in this) {
          info("no streams found for {}, choosing the best available", preselectedCdn)

          // get the best available cdn
          // obv, preselectedCdn is not in the exclude list because is not present in the map
          // so we can safely pass an empty array
          val bestCdn = this.getBestStreamByPriority(emptyArray())
          info("best available cdn list: {}", bestCdn)
          bestCdn
        } else {
          this[preselectedCdn] ?: throw createNoStreamsFoundException()
        }
      }.sortedByDescending { it.bitrate }
    }

    // prioritize flv format if user defined source format is not available
    return selectedCdnStreams.maxByOrNull { it.format == userPreferredFormat }
      ?: selectedCdnStreams.filter { it.format == VideoFormat.flv }.maxByOrNull { it.bitrate }
      ?: throw createNoStreamsFoundException()
  }

  private fun Map<String, List<StreamInfo>>.getBestStreamByPriority(excludeCdns: Array<String>): List<StreamInfo> {

    // if no streams found, throw exception
    if (this.isEmpty()) {
      throw createNoStreamsFoundException()
    }

    // sort list desc according to priority
    // priority of same cdn streams should be the same
    val sortedCdnStreams = this.toList().sortedByDescending { it.second.firstOrNull()?.priority }

    // if exclude list is empty, return the first priority cdn
    if (excludeCdns.isEmpty()) {
      return sortedCdnStreams.first().second
    }
    // get the first cdn that is not in the exclude list
    val bestCdn = sortedCdnStreams.first { it.first !in excludeCdns }.first
    return this[bestCdn] ?: run {
      // no alternative cdn found, return the first priority cdn
      sortedCdnStreams.first().second
    }
  }
}
