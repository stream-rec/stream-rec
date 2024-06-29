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

package github.hua0512.plugins.twitch.download

import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.DownloadConfig.TwitchDownloadConfig
import github.hua0512.data.platform.TwitchQuality
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.download.base.Download
import github.hua0512.plugins.twitch.danmu.TwitchDanmu
import github.hua0512.utils.nonEmptyOrNull

/**
 * @author hua0512
 * @date : 2024/5/3 21:47
 */
class Twitch(app: App, danmu: TwitchDanmu, extractor: TwitchExtractor) : Download<TwitchDownloadConfig>(app, danmu, extractor) {


  init {
    extractor.skipStreamInfo = app.config.twitchConfig.skipAds
  }


  override fun createDownloadConfig(): TwitchDownloadConfig = TwitchDownloadConfig(
    quality = app.config.twitchConfig.quality,
    authToken = app.config.twitchConfig.authToken,
  )

  override suspend fun shouldDownload(): Boolean {
    val authToken = (config.authToken?.nonEmptyOrNull() ?: app.config.twitchConfig.authToken).nonEmptyOrNull()
      ?: throw UnsupportedOperationException("Twitch requires an auth token to download")
    (extractor as TwitchExtractor).authToken = authToken

    (config.cookies ?: app.config.twitchConfig.cookies)?.nonEmptyOrNull()?.also {
      extractor.cookies = it
    }

    val mediaInfo = try {
      extractor.extract()
    } catch (e: Exception) {
      // throw if illegal argument or unsupported operation
      if (e is IllegalArgumentException || e is UnsupportedOperationException) throw e
      logger.error("Error extracting media info", e)
      return false
    }
    return getStreamInfo(mediaInfo, streamer, config)
  }

  override suspend fun <T : DownloadConfig> T.applyFilters(streams: List<StreamInfo>): StreamInfo {
    this as TwitchDownloadConfig
    val userPreferredQuality = quality ?: app.config.twitchConfig.quality
    // if source quality is selected, return the first stream
    if (userPreferredQuality == TwitchQuality.Source) {
      return streams.first()
    } else if (userPreferredQuality == TwitchQuality.Audio) {
      return streams.first { it.quality == TwitchQuality.Audio.value }
    }
    // resolution quality
    val preferredResolution = userPreferredQuality.value.removePrefix("p").toInt()
    // otherwise, filter by user defined quality
    val selectedStream = streams.filter { it.quality.contains(userPreferredQuality.value) }
    if (selectedStream.isEmpty()) {
      // if no stream found, return the first lower quality than user defined quality
      return streams.map { (it.extras["resolution"].toString().split("x").last().toIntOrNull() ?: 0) to it }.filter {
        it.first < preferredResolution
      }.maxByOrNull {
        it.first
      }?.second?.apply {
        logger.warn("No stream found with quality $userPreferredQuality, using ${this.quality} instead")
      } ?: run {
        logger.warn("No stream found with quality $userPreferredQuality, using the best available")
        streams.first()
      }
    }
    val filteredStream = selectedStream.map {
      (it.extras["resolution"].toString().split("x").last().toIntOrNull() ?: 0) to it
    }.filter {
      it.first >= preferredResolution
    }.minByOrNull {
      it.first
    }?.second ?: run {
      logger.warn("No stream found with quality $userPreferredQuality, using the best available")
      selectedStream.first()
    }
    return filteredStream
  }
}