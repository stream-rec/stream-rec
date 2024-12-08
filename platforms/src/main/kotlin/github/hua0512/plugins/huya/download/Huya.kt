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

package github.hua0512.plugins.huya.download

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig.HuyaDownloadConfig
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.download.exceptions.DownloadErrorException
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.plugins.huya.danmu.HuyaDanmu
import github.hua0512.utils.debug
import github.hua0512.utils.info
import github.hua0512.utils.warn
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


  override suspend fun shouldDownload(onLive: () -> Unit): Result<Boolean, ExtractorError> = super.shouldDownload {
    onLive()
    // bind danmu properties
    with(danmu) {
      presenterUid = extractor.presenterUid
    }
  }

  override fun getProgramArgs(): List<String> = emptyList()

  override fun onConfigUpdated(config: AppConfig) {
    super.onConfigUpdated(config)
    extractor.forceOrigin = config.huyaConfig.forceOrigin
  }

  override fun onDownloadError(exception: Exception) {
    if (exception is DownloadErrorException && exception.message.contains("403 Forbidden")) {
      warn("403 Forbidden, changing decryption method in next attempt")
      extractor.onRepeatedError(ExtractorError.ApiError(Throwable("403 Forbidden")), 0)
    }
  }


  override suspend fun applyFilters(streams: List<StreamInfo>): Result<StreamInfo, ExtractorError> {
    val config = downloadConfig
    // user defined source format
    val userPreferredFormat = (config.sourceFormat ?: app.config.huyaConfig.sourceFormat).apply {
      if (this !in streams.map { it.format }) {
        info("defined source format {} is not available, choosing the best available", this)
      }
    }
    // user defined max bit rate
    val maxUserBitRate = (config.maxBitRate ?: app.config.huyaConfig.maxBitRate) ?: 10000
    // user selected cdn
    val preselectedCdn = (config.primaryCdn ?: app.config.huyaConfig.primaryCdn).uppercase()

    // drop all streams with bit rate higher than user defined max bit rate
    val selectedCdnStreams = withContext(Dispatchers.Default) {
      streams.asSequence().filter {
        it.bitrate <= maxUserBitRate
      }.groupBy {
        it.extras["cdn"]
      }.let { cdnGroups ->
        cdnGroups[preselectedCdn]?.takeIf { it.isNotEmpty() } ?: run {
          debug("streams : {}", streams)
          debug("filtered streams : {}", this)
          info("cdn($preselectedCdn) has no streams, choosing the best available")
          // get the best available cdn
          // obv, preselectedCdn is not in the exclude list because is not present in the map
          // so we can safely pass an empty array
          val computeResult = cdnGroups.getBestStreamByPriority(emptyArray())
          if (computeResult.isErr) {
            return@run emptyList()
          }
          computeResult.value.also {
            info("best available cdn stream list: {}", it)
          }
        }
      }.sortedByDescending { it.bitrate }
    }

    if (selectedCdnStreams.isEmpty()) return Err(ExtractorError.NoStreamsFound)

    val preferredFormatStream = selectedCdnStreams.filter { it.format == userPreferredFormat }.maxByOrNull { it.bitrate }

    debug("user preferred format stream: {}", preferredFormatStream)
    if (preferredFormatStream != null && preferredFormatStream.bitrate.toInt() != maxUserBitRate) {
      warn("user preferred bitrate {} is not available, falling back to the best available: {}", maxUserBitRate, preferredFormatStream.bitrate)
    }
    // prioritize flv format if user defined source format is not available
    val flvFormatStreams = { selectedCdnStreams.filter { it.format == VideoFormat.flv }.maxByOrNull { it.bitrate } }

    return Ok(preferredFormatStream ?: flvFormatStreams() ?: selectedCdnStreams.first())

  }

  private fun Map<String?, List<StreamInfo>>.getBestStreamByPriority(excludeCdns: Array<String>): Result<List<StreamInfo>, ExtractorError> {

    // if no streams found, return error
    if (this.isEmpty()) {
      return Err(ExtractorError.NoStreamsFound)
    }

    // sort list desc according to priority
    // priority of same cdn streams should be the same
    val sortedCdnStreams = this.toList().sortedByDescending { it.second.firstOrNull()?.priority }

    // if exclude list is empty, return the first priority cdn streams
    if (excludeCdns.isEmpty()) {
      return Ok(sortedCdnStreams.first().second)
    }
    // get the first cdn that is not in the exclude list
    val bestCdn = sortedCdnStreams.first { it.first !in excludeCdns }.first
    val bestCdnStreams = this[bestCdn] ?: run {
      // no alternative cdn found, return the first priority cdn
      sortedCdnStreams.first().second
    }
    return Ok(bestCdnStreams)
  }
}
