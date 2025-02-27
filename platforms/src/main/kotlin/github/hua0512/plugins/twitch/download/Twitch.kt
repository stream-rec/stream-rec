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

package github.hua0512.plugins.twitch.download

import com.github.michaelbull.result.Result
import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig.TwitchDownloadConfig
import github.hua0512.data.config.engine.DownloadEngines
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.download.base.HlsPlatformDownloader
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
) : HlsPlatformDownloader<TwitchDownloadConfig>(app, danmu, extractor) {


  override suspend fun shouldDownload(onLive: () -> Unit): Result<Boolean, ExtractorError> {
    val authToken = downloadConfig.authToken.orEmpty().ifEmpty {
      ""
//      throw InvalidExtractionUrlException("Twitch requires an auth token to download")
    }
    extractor.authToken = authToken
    // update extractor params
    updateParams(app.config)
    return super.shouldDownload(onLive)
  }

  override fun getProgramArgs(): List<String> = buildList {
    val config = app.config.twitchConfig
    // check if skip ads is enabled
    if (config.skipAds) {
      // add skip ads to streamlink args
      add("--twitch-disable-ads")
    }
    // configure streamlink-ttvlol options
    config.twitchProxyPlaylist?.nonEmptyOrNull()?.let { add("--twitch-proxy-playlist=$it") }
    config.twitchProxyPlaylistExclude?.nonEmptyOrNull()?.let { add("--twitch-proxy-playlist-exclude=$it") }
    if (config.twitchProxyPlaylistFallback) add("--twitch-proxy-playlist-fallback")
  }

  private fun updateParams(config: AppConfig) {
    val engine = streamer.engine ?: DownloadEngines.fromString(config.engine)
    if (engine is DownloadEngines.FFMPEG || engine is DownloadEngines.STREAMLINK) {
      extractor.skipStreamInfo =
        app.config.twitchConfig.skipAds || app.config.twitchConfig.twitchProxyPlaylist?.nonEmptyOrNull() != null
    } else {
      extractor.skipStreamInfo = false
    }
  }

  override fun onConfigUpdated(config: AppConfig) {
    super.onConfigUpdated(config)
    updateParams(config)
  }

}