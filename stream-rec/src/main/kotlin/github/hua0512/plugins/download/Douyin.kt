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

package github.hua0512.plugins.download

import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig.DouyinDownloadConfig
import github.hua0512.data.platform.DouyinQuality
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.base.Download
import github.hua0512.plugins.danmu.douyin.DouyinDanmu
import github.hua0512.utils.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*

/**
 * This class represents a Douyin downloader.
 *
 * @property app The [App] instance.
 * @property danmu The [DouyinDanmu] instance.
 * @property regexPattern The regular expression pattern to match Douyin URLs.
 * @property streamer The [Streamer] instance.
 * @property downloadTitle The title of the downloaded video.
 * @property downloadUrl The URL of the video to be downloaded.
 */
class Douyin(app: App, danmu: DouyinDanmu) : Download(app, danmu) {

  override val regexPattern: String = REGEX

  override suspend fun shouldDownload(streamer: Streamer): Boolean {
    this.streamer = streamer

    val roomId = extractDouyinRoomId(streamer.url) ?: false

    val config = streamer.downloadConfig as? DouyinDownloadConfig ?: DouyinDownloadConfig()

    val cookies = (config.cookies ?: app.config.douyinConfig.cookies).let {
      if (it.isNullOrEmpty()) {
        throw IllegalArgumentException("(${streamer.name}) Please provide douyin cookies!")
      }
      populateDouyinCookieMissedParams(it, app.client)
    }

    val response = withIOContext {
      app.client.get("https://live.douyin.com/webcast/room/web/enter/") {
        headers {
          commonHeaders.forEach { append(it.first, it.second) }
          append(HttpHeaders.Referrer, "https://live.douyin.com")
          append(HttpHeaders.Cookie, cookies)
        }
        commonDouyinParams.forEach { (key, value) ->
          parameter(key, value)
        }
        parameter("web_rid", roomId)
      }
    }

    if (response.status != HttpStatusCode.OK) {
      logger.debug("(${streamer.name}) response status is not OK : {}", response.status)
      return false
    }

    val data = response.bodyAsText()
//    logger.debug("(${streamer.name}) data: $data")
    val json = app.json.parseToJsonElement(data)
    val liveData = json.jsonObject["data"]?.jsonObject?.get("data")?.jsonArray?.get(0)?.jsonObject ?: run {
      logger.debug("(${streamer.name}) unable to get live data")
      return false
    }

    downloadTitle = liveData["title"]?.jsonPrimitive?.content ?: run {
      logger.debug("(${streamer.name}) unable to get live title")
      return false
    }

    val status = liveData["status"]?.jsonPrimitive?.int ?: run {
      logger.debug("(${streamer.name}) unable to get live status")
      return false
    }

    if (status != 2) {
      logger.debug("(${streamer.name}) is not live")
      return false
    }

    val selectedQuality = (config.quality?.value ?: app.config.douyinConfig.quality.value).run {
      this.ifEmpty { DouyinQuality.origin.value }
    }

    val streamDataJson =
      liveData["stream_url"]?.jsonObject?.get("live_core_sdk_data")?.jsonObject?.get("pull_data")?.jsonObject?.get("stream_data")?.jsonPrimitive?.content
        ?: run {
          logger.error("(${streamer.name}) unable to get stream data")
          return false
        }

    val streamsData = app.json.parseToJsonElement(streamDataJson).jsonObject["data"]?.jsonObject ?: run {
      logger.error("(${streamer.name}) unable to parse stream data")
      return false
    }

    val streamData = streamsData[selectedQuality]?.jsonObject ?: run {
      logger.error("(${streamer.name}) unable to get stream data for quality: $selectedQuality")
      return false
    }

    downloadUrl = streamData["main"]?.jsonObject?.get("flv")?.jsonPrimitive?.content ?: run {
      logger.error("(${streamer.name}) unable to get stream url")
      return false
    }

    logger.debug("({}) json: {}", streamer.name, streamData)
    return true
  }

  @OptIn(InternalCoroutinesApi::class)
  companion object {
    private const val BASE_URL = "https://www.douyin.com"

    internal const val REGEX = "(?:https?://)?(?:www\\.)?(?:live\\.)?douyin\\.com/([a-zA-Z0-9]+)"


    /**
     * The `ttwid` parameter from the Douyin cookies.
     */
    private lateinit var douyinTTwid: String

    /**
     * The `msToken` parameter to be used in Douyin requests.
     */
    private lateinit var douyinMsToken: String


    /**
     * A map of common parameters used for making requests to the Douyin API.
     *
     * The map contains the following key-value pairs:
     * - "aid" - The Douyin application ID
     * - "device_platform" - The platform of the device making the request (e.g., "web")
     * - "browser_language" - The language of the browser making the request (e.g., "zh-CN")
     * - "browser_platform" - The platform of the browser making the request (e.g., "Win32")
     * - "browser_name" - The name of the browser making the request (e.g., "Chrome")
     * - "browser_version" - The version of the browser making the request (e.g., "98.0.4758.102")
     * - "compress" - The compression method used for the response (e.g., "gzip")
     * - "signature" - The signature for the request
     * - "heartbeatDuration" - The duration of the heartbeat in milliseconds
     */
    internal val commonDouyinParams = mapOf(
      "aid" to "6383",
      "device_platform" to "web",
      "browser_language" to "zh-CN",
      "browser_platform" to "Win32",
      "browser_name" to "Chrome",
      "browser_version" to "98.0.4758.102",
      "compress" to "gzip",
      "signature" to "00000000",
      "heartbeatDuration" to "0"
    )

    /**
     * Extracts the Douyin room ID from the specified URL.
     *
     * @param url The URL from which to extract the Douyin room ID
     * @return The Douyin room ID, or `null` if the room ID could not be extracted
     */
    internal fun extractDouyinRoomId(url: String): String? {
      if (url.isEmpty()) return null
      return try {
        val roomIdPattern = REGEX.toRegex()
        roomIdPattern.find(url)?.groupValues?.get(1) ?: run {
          logger.error("Failed to get douyin room id from url: $url")
          return null
        }
      } catch (e: Exception) {
        logger.error("Failed to get douyin room id from url: $url", e)
        null
      }
    }

    /**
     * Populates the missing parameters (ttwid or msToken) in the specified Douyin cookies.
     *
     *
     * @param cookies The Douyin cookies to populate
     * @param client The HTTP client to use for making requests
     * @return The Douyin cookies with the missing parameters populated
     */
    suspend fun populateDouyinCookieMissedParams(cookies: String, client: HttpClient): String {
      if (cookies.isEmpty()) {
        throw IllegalArgumentException("Empty cookies")
      }
      var finalCookies = cookies
      if ("ttwid" !in cookies) {
        val ttwid = getDouyinTTwid(client)
        finalCookies += "; ttwid=$ttwid"
      }
      if ("msToken" !in cookies) {
        val msToken = generateDouyinMsToken()
        finalCookies += "; msToken=$msToken"
      }
      return finalCookies
    }

    /**
     * Generates a random string to be used as the `msToken` parameter in Douyin requests.
     *
     * @param length The length of the random string to generate
     * @return A random string to be used as the `msToken` parameter in Douyin requests
     */
    private fun generateDouyinMsToken(length: Int = 107): String {
      synchronized(this) {
        if (::douyinMsToken.isInitialized) return douyinMsToken

        // generate a random string, with length 107
        val source = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789=_"
        val random = Random()
        val sb = StringBuilder()
        for (i in 0 until length) {
          sb.append(source[random.nextInt(source.length)])
        }
        return sb.toString().also {
          douyinMsToken = it
          logger.info("generated douyin msToken: $it")
        }
      }
    }

    /**
     * Makes a request to the Douyin API to get the `ttwid` parameter from the cookies.
     *
     * @param client The HTTP client to use for making requests
     * @return The `ttwid` parameter from the Douyin cookies
     */
    private suspend fun getDouyinTTwid(httpClient: HttpClient): String {
      val response = httpClient.get("https://live.douyin.com/1-2-3-4-5-6-7-8-9-0") {
        commonDouyinParams.forEach { (key, value) ->
          parameter(key, value)
        }
        commonHeaders.forEach { (key, value) ->
          header(key, value)
        }
        header(HttpHeaders.Referrer, "https://live.douyin.com")
      }
      val cookies = response.headers[HttpHeaders.SetCookie] ?: ""
      val ttwidPattern = "ttwid=([^;]*)".toRegex()
      val ttwid = ttwidPattern.find(cookies)?.groupValues?.get(1) ?: ""
      if (ttwid.isEmpty()) {
        throw Exception("Failed to get ttwid from cookies")
      }
      synchronized(this) {
        if (!::douyinTTwid.isInitialized) {
          douyinTTwid = ttwid
          logger.info("got douyin ttwid: $ttwid")
        }
      }
      return ttwid
    }
  }

}