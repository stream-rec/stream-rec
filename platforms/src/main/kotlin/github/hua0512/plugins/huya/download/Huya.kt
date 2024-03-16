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
import github.hua0512.data.config.DownloadConfig.HuyaDownloadConfig
import github.hua0512.data.stream.StreamInfo
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.base.Download
import github.hua0512.plugins.huya.danmu.HuyaDanmu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Huya(app: App, danmu: HuyaDanmu, extractor: HuyaExtractor) : Download(app, danmu, extractor) {

  override suspend fun shouldDownload(streamer: Streamer): Boolean {
    this.streamer = streamer

    val userConfig: HuyaDownloadConfig = if (streamer.templateStreamer != null) {
      streamer.templateStreamer!!.downloadConfig?.run {
        HuyaDownloadConfig(
          primaryCdn = app.config.huyaConfig.primaryCdn
        ).also {
          it.danmu = this.danmu
          it.maxBitRate = this.maxBitRate
          it.outputFileFormat = this.outputFileFormat
          it.outputFileName = this.outputFileName
          it.outputFolder = this.outputFolder
          it.onPartedDownload = this.onPartedDownload ?: emptyList()
          it.onStreamingFinished = this.onStreamingFinished ?: emptyList()
        }
      } ?: throw IllegalArgumentException("${streamer.name} has template streamer but no download config")
    } else {
      streamer.downloadConfig as? HuyaDownloadConfig ?: HuyaDownloadConfig()
    }

    val cookies = (userConfig.cookies ?: app.config.huyaConfig.cookies)?.also {
      extractor.cookies = it
    }

    val mediaInfo = try {
      withContext(Dispatchers.Default) {
        extractor.extract()
      }
    } catch (e: Exception) {
      logger.error("Error extracting media info", e)
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
    // user defined source format
    val userPreferredFormat = (userConfig.sourceFormat ?: app.config.huyaConfig.sourceFormat).apply {
      if (this !in mediaInfo.streams.map { it.format }) {
        logger.info("${streamer.name} defined source format $this is not available, choosing the best available")
      }
    }
    // user defined max bit rate
    val maxUserBitRate = (userConfig.maxBitRate ?: app.config.huyaConfig.maxBitRate) ?: 10000
    // user selected cdn
    var preselectedCdn = userConfig.primaryCdn ?: app.config.huyaConfig.primaryCdn
    preselectedCdn = preselectedCdn.uppercase()

    // drop all streams with bit rate higher than user defined max bit rate
    val selectedCdnStreams = withContext(Dispatchers.Default) {
      mediaInfo.streams.filter {
        it.bitrate <= maxUserBitRate
      }.groupBy {
        it.extras["cdn"]
      }.run {
        this as Map<String, List<StreamInfo>>

        if (preselectedCdn !in this) {
          logger.info("${streamer.name} no streams found for $preselectedCdn, choosing the best available")

          // get the best available cdn
          // obv, preselectedCdn is not in the exclude list because is not in the map,
          // so we can safely pass an empty array
          val bestCdn = this.getBestStreamByPriority(emptyArray())
          logger.info("${streamer.name} best available cdn is $bestCdn")
          bestCdn
        } else {
          this[preselectedCdn] ?: throw IllegalArgumentException("${streamer.name} no streams found")
        }
      }.sortedByDescending { it.bitrate }
    }


    // check if has more than one stream
    val finalStreamInfo = selectedCdnStreams.maxByOrNull { it.format == userPreferredFormat }
      ?: selectedCdnStreams.firstOrNull().also {
        logger.info("${streamer.name} no streams found for $userPreferredFormat, choosing the best available")
      }
      ?: throw IllegalStateException("${streamer.name} no streams found")
    downloadFileFormat = finalStreamInfo.format
    downloadUrl = finalStreamInfo.url
    return true
  }
}

private fun Map<String, List<StreamInfo>>.getBestStreamByPriority(excludeCdns: Array<String>): List<StreamInfo> {
  // sort list desc according to priority
  // priority is the same for all streams of the same cdn
  val sortedCdnStreams = this.toList().sortedByDescending { it.second.firstOrNull()?.priority }
  // get the first cdn that is not in the exclude list
  val bestCdn = sortedCdnStreams.firstOrNull { it.first !in excludeCdns }?.first
  return this[bestCdn]!!
}