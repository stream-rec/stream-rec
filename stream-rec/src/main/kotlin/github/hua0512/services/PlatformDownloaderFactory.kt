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

package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.douyin.danmu.DouyinDanmu
import github.hua0512.plugins.douyin.download.Douyin
import github.hua0512.plugins.douyin.download.DouyinExtractor
import github.hua0512.plugins.douyu.danmu.DouyuDanmu
import github.hua0512.plugins.douyu.download.Douyu
import github.hua0512.plugins.douyu.download.DouyuExtractor
import github.hua0512.plugins.download.base.IPlatformDownloaderFactory
import github.hua0512.plugins.huya.danmu.HuyaDanmu
import github.hua0512.plugins.huya.download.Huya
import github.hua0512.plugins.huya.download.HuyaExtractor
import github.hua0512.plugins.huya.download.HuyaExtractorV2
import github.hua0512.plugins.pandatv.danmu.PandaTvDanmu
import github.hua0512.plugins.pandatv.download.PandaTv
import github.hua0512.plugins.pandatv.download.PandaTvExtractor
import github.hua0512.plugins.twitch.danmu.TwitchDanmu
import github.hua0512.plugins.twitch.download.Twitch
import github.hua0512.plugins.twitch.download.TwitchExtractor

/**
 * Platform downloader factory
 * TODO: Use KSP to generate this class
 * @author hua0512
 * @date : 2024/5/17 11:58
 */
object PlatformDownloaderFactory : IPlatformDownloaderFactory {

  override fun createDownloader(app: App, platform: StreamingPlatform, url: String) = when (platform) {
    StreamingPlatform.HUYA -> {
      val useMobile = app.config.huyaConfig.useMobileApi
      val isNumericUrl = useMobile && url.split("/").last().matches(Regex("\\d+"))
      // use v2 extractor for numeric urls
      if (isNumericUrl) {
        Huya(app, HuyaDanmu(app), HuyaExtractorV2(app.client, app.json, url))
      } else {
        Huya(app, HuyaDanmu(app), HuyaExtractor(app.client, app.json, url))
      }
    }

    StreamingPlatform.DOUYIN -> Douyin(app, DouyinDanmu(app), DouyinExtractor(app.client, app.json, url))
    StreamingPlatform.DOUYU -> Douyu(app, DouyuDanmu(app), DouyuExtractor(app.client, app.json, url))
    StreamingPlatform.TWITCH -> Twitch(app, TwitchDanmu(app), TwitchExtractor(app.client, app.json, url))
    StreamingPlatform.PANDATV -> PandaTv(app, PandaTvDanmu(app), PandaTvExtractor(app.client, app.json, url))
    else -> throw IllegalArgumentException("Platform not supported")
  }
}