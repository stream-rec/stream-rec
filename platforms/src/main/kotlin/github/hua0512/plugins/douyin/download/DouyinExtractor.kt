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

package github.hua0512.plugins.douyin.download

import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.download.COMMON_HEADERS
import github.hua0512.plugins.download.COMMON_USER_AGENT
import github.hua0512.utils.generateRandomString
import github.hua0512.utils.nonEmptyOrNull
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 *
 * Douyin live stream extractor
 * @author hua0512
 * @date : 2024/3/16 13:10
 */
class DouyinExtractor(http: HttpClient, json: Json, override val url: String) : Extractor(http, json) {

  override val regexPattern: Regex = URL_REGEX.toRegex()

  private lateinit var roomId: String

  private var jsonData: JsonElement = JsonNull

  init {
    platformHeaders[HttpHeaders.Referrer] = LIVE_DOUYIN_URL
    commonDouyinParams.forEach { (key, value) ->
      platformParams[key] = value
    }
  }

  override fun match(): Boolean {
    roomId = extractDouyinRoomId(url) ?: return false
    return true
  }

  override suspend fun isLive(): Boolean {
    // initialize cookies
    cookies = cookies.nonEmptyOrNull().apply { populateDouyinCookieMissedParams(cookies, http) }
      ?: populateDouyinCookieMissedParams(cookies, http)
    val response = getResponse("${LIVE_DOUYIN_URL}/webcast/room/web/enter/") {
      parameter("web_rid", roomId)
    }
    if (response.status != HttpStatusCode.OK) throw IllegalStateException("$url failed to get live data")
    val data = response.bodyAsText()
    jsonData = json.parseToJsonElement(data)
    val liveData = jsonData.jsonObject["data"]?.jsonObject?.get("data")?.jsonArray?.get(0)?.jsonObject ?: run {
      logger.debug("$url unable to get live data")
      return false
    }
    val status = liveData["status"]?.jsonPrimitive?.int ?: run {
      logger.debug("$url unable to get live status")
      return false
    }
    return status == 2
  }

  override suspend fun extract(): MediaInfo {
    val isLive = isLive()

    val liveData = jsonData.jsonObject["data"]!!.jsonObject["data"]!!.jsonArray[0].jsonObject

    val title = liveData["title"]!!.jsonPrimitive.content
    val owner = liveData["owner"]
    val nickname = owner?.jsonObject?.get("nickname")?.jsonPrimitive?.content ?: ""

    val mediaInfo = MediaInfo(LIVE_DOUYIN_URL, title, nickname, "", "", isLive)
    // if not live, return basic media info
    if (!isLive) return mediaInfo

    // avatar is not available when not live
    val avatar = owner?.jsonObject?.get("avatar_thumb")?.jsonObject?.get("url_list")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content ?: run {
      logger.debug("$url unable to get avatar")
      ""
    }

    val cover = liveData["cover"]!!.jsonObject["url_list"]?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content ?: run {
      logger.debug("$url unable to get cover")
      ""
    }

    val pullData = liveData["stream_url"]!!.jsonObject["live_core_sdk_data"]!!.jsonObject["pull_data"]?.jsonObject ?: run {
      logger.debug("$url unable to get stream data")
      return mediaInfo.copy(coverUrl = cover)
    }

    val qualities = pullData["options"]!!.jsonObject["qualities"]?.jsonArray?.map {
      val current = it.jsonObject
      val name = current["name"]!!.jsonPrimitive.content
      val sdkKey = current["sdk_key"]!!.jsonPrimitive.content
      val bitrate = current["v_bit_rate"]!!.jsonPrimitive.int
      Triple(sdkKey, name, bitrate)
    }

    val streamDataJson = pullData["stream_data"]?.jsonPrimitive?.content
      ?: throw IllegalStateException("$url failed to get stream data")

    val streamsData =
      json.parseToJsonElement(streamDataJson).jsonObject["data"]?.jsonObject ?: throw IllegalStateException("$url failed to get stream data")

    val streams = qualities?.flatMap { (sdkKey, name, bitrate) ->
      val stream = streamsData[sdkKey]?.jsonObject ?: return@flatMap emptyList()
      val main = stream["main"]?.jsonObject ?: return@flatMap emptyList()
      val flvInfo = main["flv"]?.jsonPrimitive?.content?.nonEmptyOrNull()?.let {
        StreamInfo(
          url = it,
          format = VideoFormat.flv,
          quality = name,
          bitrate = bitrate.toLong(),
          frameRate = 0.0,
          extras = mapOf("sdkKey" to sdkKey)
        )
      }
      // hls is not always available
      // hls may have no sound
      val hlsInfo = main["hls"]?.jsonPrimitive?.content?.nonEmptyOrNull()?.let {
        StreamInfo(
          url = it,
          format = VideoFormat.hls,
          quality = name,
          bitrate = bitrate.toLong(),
          frameRate = 0.0,
          extras = mapOf("sdkKey" to sdkKey)
        )
      }
      listOfNotNull(flvInfo, hlsInfo)
    } ?: emptyList()

    return mediaInfo.copy(artistImageUrl = avatar, coverUrl = cover, streams = streams)
  }

  companion object {
    const val URL_REGEX = "(?:https?://)?(?:www\\.)?(?:live\\.)?douyin\\.com/([a-zA-Z0-9]+)"
    const val LIVE_DOUYIN_URL = "https://live.douyin.com"

    // cookie parameters
    private var NONCE: String? = null
    private var TT_WID: String? = null
    private var MS_TOKEN: String? = null

    /**
     * Extracts the Douyin room ID from the specified URL.
     *
     * @param url The URL from which to extract the Douyin room ID
     * @return The Douyin room ID, or `null` if the room ID could not be extracted
     */
    internal fun extractDouyinRoomId(url: String): String? {
      if (url.isEmpty()) return null
      return try {
        val roomIdPattern = URL_REGEX.toRegex()
        roomIdPattern.find(url)?.groupValues?.get(1) ?: run {
          logger.error("Failed to get douyin room id from url: $url")
          return null
        }
      } catch (e: Exception) {
        throw IllegalArgumentException("Failed to get douyin room id from url: $url")
      }
    }


    /**
     * Generates a random string to be used as the `__ac_nonce` parameter in Douyin requests.
     * The generated string is 21 characters long.
     * @return A random string to be used as the `__ac_nonce` parameter in Douyin requests
     */
    private fun generateNonce(): String = synchronized(this) {
      if (NONCE != null) return NONCE!!
      return generateRandomString(21).apply {
        NONCE = this
        logger.info("generated nonce: $this")
      }
    }

    /**
     * Populates the missing parameters (ttwid, __ac_nonce or msToken) in the specified Douyin cookies.
     *
     * @param cookies The Douyin cookies to populate
     * @param client The HTTP client to use for making requests
     * @return The Douyin cookies with the missing parameters populated
     */
    suspend fun populateDouyinCookieMissedParams(cookies: String, client: HttpClient): String {
      val map = parseCookies(cookies).toMutableMap().apply {
        getOrPut("ttwid") { getDouyinTTwid(client) }
        getOrPut("__ac_nonce") { generateNonce() }
        getOrPut("msToken") { generateDouyinMsToken() }
      }
      return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }


    /**
     * Generates a random string to be used as the `msToken` parameter in Douyin requests.
     *
     * @param length The length of the random string to generate
     * @return A random string to be used as the `msToken` parameter in Douyin requests
     */
    private fun generateDouyinMsToken(length: Int = 116): String {
      synchronized(this) {
        if (MS_TOKEN != null) return MS_TOKEN!!

        // generate a random string, with length 107
        val generated = generateRandomString(length)
        return generated.also {
          MS_TOKEN = it
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
    private suspend fun getDouyinTTwid(client: HttpClient): String {
      if (TT_WID != null) return TT_WID!!
      val response = client.get("${LIVE_DOUYIN_URL}/") {
        commonDouyinParams.forEach { (key, value) ->
          parameter(key, value)
        }
        COMMON_HEADERS.forEach { (key, value) ->
          header(key, value)
        }
        header(HttpHeaders.Referrer, LIVE_DOUYIN_URL)
      }

      val cookies = response.headers[HttpHeaders.SetCookie] ?: ""
      val ttwidPattern = "ttwid=([^;]*)".toRegex()
      val ttwid = ttwidPattern.find(cookies)?.groupValues?.get(1) ?: ""
      if (ttwid.isEmpty()) {
        throw Exception("Failed to get ttwid from cookies")
      }
      synchronized(this) {
        if (TT_WID == null) {
          TT_WID = ttwid
          logger.info("got douyin ttwid: $ttwid")
        }
      }
      return ttwid
    }

    internal var USER_ID: String? = null
      get() {
        if (field != null) return field!!
        synchronized(this) {
          if (field == null) {
            field = Random.nextLong(730_000_000_000_000_0000L, 7_999_999_999_999_999_999L).toString()
            logger.info("generated douyin user id: $field")
          }
        }
        return field!!
      }

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
      "app_name" to "douyin_web",
      "version_code" to "180800",
      "webcast_sdk_version" to "1.0.14-beta.0",
      "update_version_code" to "1.0.14-beta.0",
      "compress" to "gzip",
      "device_platform" to "web",
      "browser_language" to "zh-CN",
      "browser_platform" to "Win32",
      "browser_name" to "Mozilla",
      "browser_version" to COMMON_USER_AGENT.removePrefix("Mozilla/").trim(),
      "host" to "https://live.douyin.com",
      "aid" to "6383",
      "live_id" to "1",
      "did_rule" to "3",
      "endpoint" to "live_pc",
      "identity" to "audience",
      "heartbeatDuration" to "0",
    )
  }

}
