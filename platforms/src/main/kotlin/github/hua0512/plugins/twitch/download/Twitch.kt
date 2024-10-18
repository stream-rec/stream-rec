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
import github.hua0512.plugins.base.exceptions.InvalidExtractionUrlException
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.plugins.twitch.danmu.TwitchDanmu
import github.hua0512.utils.nonEmptyOrNull

/**
 * Twitch downloader.
 * @author hua0512
 * @date : 2024/5/3 21:47
 */
class Twitch(
  app: App,
  danmu: TwitchDanmu,
  override val extractor: TwitchExtractor,
) : PlatformDownloader<TwitchDownloadConfig>(app, danmu, extractor) {


  init {
    extractor.skipStreamInfo =
      app.config.twitchConfig.skipAds || app.config.twitchConfig.twitchProxyPlaylist?.nonEmptyOrNull() != null
  }


  override suspend fun shouldDownload(onLive: () -> Unit): Boolean {
    val authToken = downloadConfig.authToken.orEmpty().ifEmpty {
      throw InvalidExtractionUrlException("Twitch requires an auth token to download")
    }
    extractor.authToken = authToken
    return super.shouldDownload(onLive)
  }

  override fun getPlatformHeaders(): Map<String, String> = extractor.getRequestHeaders()

  override fun getProgramArgs(): List<String> = buildList {
    val config = app.config.twitchConfig
    // check if skip ads is enabled
    if (config.skipAds) {
      // add skip ads to streamlink args
      add("--twitch-disable-ads")
    }
    // configure streamlink-ttvlol
    config.twitchProxyPlaylist?.nonEmptyOrNull()?.let { add("--twitch-proxy-playlist=$it") }
    config.twitchProxyPlaylistExclude?.nonEmptyOrNull()?.let { add("--twitch-proxy-playlist-exclude=$it") }
    if (config.twitchProxyPlaylistFallback) add("--twitch-proxy-playlist-fallback")
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