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

package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.IExtractorFactory
import github.hua0512.plugins.douyin.download.DouyinStrevExtractor
import github.hua0512.plugins.douyu.download.DouyuExtractor
import github.hua0512.plugins.huya.download.HuyaExtractor
import github.hua0512.plugins.huya.download.HuyaExtractorV2
import github.hua0512.plugins.pandatv.download.PandaTvExtractor
import github.hua0512.plugins.twitch.download.TwitchExtractor
import github.hua0512.plugins.weibo.download.WeiboExtractor
import github.hua0512.utils.mainLogger
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class ExtractorFactory(val app: App, val client: HttpClient, val json: Json) : IExtractorFactory {


  val extractors = mapOf<String, KClass<*>>(
    "live.douyin.com" to DouyinStrevExtractor::class,
    "www.douyu.com" to DouyuExtractor::class,
    "www.huya.com" to HuyaExtractor::class,
    "www.pandalive.co" to PandaTvExtractor::class,
    "www.twitch.tv" to TwitchExtractor::class,
    "www.weibo.com" to WeiboExtractor::class,
  )


  override fun getExtractorFromUrl(url: String, params: Map<String, List<String>>?): Extractor? {

    if (url.contains("huya.com") && params?.get("type")?.firstOrNull() == "mobileApi") {
      return HuyaExtractorV2(client, json, url)
    }

    // for example live.douyin.com
    val host = Url(url).host

    mainLogger.debug("host : $host")

    val extractor = extractors[host] ?: return null

    // TODO: Should migrate to use Ksp to generate the code instead of reflection, I hate it so much

    val extractorInstance = extractor.primaryConstructor?.let {
      it.call(client, json, url) as Extractor
    }
    return extractorInstance?.populateExtractorParams()
  }

  /**
   * Populates the extractor parameters based on the app configuration.
   */
  private fun Extractor.populateExtractorParams(): Extractor {
    if (this is TwitchExtractor) {
      // populate twitch extractor params
      this.authToken = app.config.twitchConfig.authToken
    }
    return this
  }


}