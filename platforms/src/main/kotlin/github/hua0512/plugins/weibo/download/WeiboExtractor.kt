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

package github.hua0512.plugins.weibo.download

import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.exceptions.InvalidExtractionParamsException
import github.hua0512.plugins.base.exceptions.InvalidExtractionResponseException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * @author hua0512
 * @date : 2024/10/19 21:35
 */
class WeiboExtractor(override val http: HttpClient, override val json: Json, override val url: String) : Extractor(http, json) {


  companion object {
    /**
     * URL regex pattern
     * urls like:
     * https://weibo.com/u/6034381748
     * https://weibo.com/l/wblive/p/show/1022:2321325026370190442592
     */
    private const val URL_REGEX_PATTERN = "^(https://(?:www\\.)?weibo\\.com/(u/\\d+|l/wblive/p/show/\\d+:\\d+))(?:[?#].*)?$"

    private const val DEFAULT_COOKIE =
      "XSRF-TOKEN=qAP-pIY5V4tO6blNOhA4IIOD; SUB=_2AkMRNMCwf8NxqwFRmfwWymPrbI9-zgzEieKnaDFrJRMxHRl-yT9kqmkhtRB6OrTuX5z9N_7qk9C3xxEmNR-8WLcyo2PM; SUBP=0033WrSXqPxfM72-Ws9jqgMF55529P9D9WWemwcqkukCduUO11o9sBqA; WBPSESS=Wk6CxkYDejV3DDBcnx2LOXN9V1LjdSTNQPMbBDWe4lO2HbPmXG_coMffJ30T-Avn_ccQWtEYFcq9fab1p5RR6PEI6w661JcW7-56BszujMlaiAhLX-9vT4Zjboy1yf2l"

    private const val STATUS_API = "https://weibo.com/ajax/statuses/mymblog"
    private const val APP_LIVE_API = "https://weibo.com/l/pc/anchor/live"

    private const val UID_REGEX_PATTERN = "u/([0-9]+)"

    private const val BASE_URL = "https://weibo.com"

  }

  private var rid: String = ""

  private var title = ""
  private var avatarUrl = ""
  private var coverUrl = ""
  private var artist = ""

  private var dataJson: JsonElement = JsonNull

  init {
    platformHeaders[HttpHeaders.Referrer] = BASE_URL
  }


  /**
   * Regex pattern
   */
  override val regexPattern: Regex = URL_REGEX_PATTERN.toRegex()

  override suspend fun isLive(): Boolean {
    cookies = cookies.ifEmpty { DEFAULT_COOKIE }
    if (url.contains("show/")) {
      rid = url.split("?").first().split("show/")[1]
    } else {
      // uid is the user id (u/7676267963)
      // extract the uid from the url
      rid = ""
      val uidRegex = UID_REGEX_PATTERN.toRegex()
      val uid = uidRegex.find(url)?.groupValues?.get(1) ?: ""
      if (uid.isEmpty()) return false

      val response = getResponse(STATUS_API) {
        parameter("uid", uid)
        parameter("page", "1")
        parameter("feature", "0")
      }
      if (response.status != HttpStatusCode.OK) {
        throw InvalidExtractionResponseException("$url invalid response status: ${response.status}")
      }
      val json = response.body<JsonElement>()
      val data = json.jsonObject["data"]?.jsonObject ?: throw InvalidExtractionParamsException("$url data not found")
      dataJson = data
      val list = data["list"]?.jsonArray ?: throw InvalidExtractionParamsException("$url list not found")
      if (list.isEmpty()) return false

      for (i in 0 until list.size) {
        val item = list[i].jsonObject
        // check if page_info is present
        val pageInfo = item["page_info"]?.jsonObject ?: continue
        // check if object_type is present and equals to live
        val objectType = pageInfo["object_type"]?.jsonPrimitive?.content ?: continue
        if (objectType == "live") {
          rid = pageInfo["object_id"]?.jsonPrimitive?.content ?: throw InvalidExtractionParamsException("$url rid not found")
          break
        }
      }
    }

    if (rid.isEmpty()) {
      // extract title
      val list = dataJson.jsonObject["list"]?.jsonArray ?: throw InvalidExtractionParamsException("$url list info not found")
      dataJson = JsonNull
      if (list.isEmpty()) return false
      val user = list.firstOrNull()?.jsonObject["user"]?.jsonObject ?: throw InvalidExtractionParamsException("$url user info not found")
      artist = user["screen_name"]?.jsonPrimitive?.content ?: ""
      avatarUrl = user["profile_image_url"]?.jsonPrimitive?.content ?: ""
      return false
    }

    val response = getResponse(APP_LIVE_API) {
      parameter("live_id", rid)
    }

    if (response.status != HttpStatusCode.OK) {
      throw InvalidExtractionResponseException("$url invalid response status: ${response.status}")
    }
    val json = response.body<JsonElement>()
    val data = json.jsonObject["data"]?.jsonObject ?: throw InvalidExtractionParamsException("$url data not found")
    dataJson = data
    val item = data["item"]?.jsonObject ?: throw InvalidExtractionParamsException("$url item not found")
    val liveStatus = item["status"]?.jsonPrimitive?.intOrNull ?: 0
    return liveStatus == 1
  }

  override suspend fun extract(): MediaInfo {
    val isLive = isLive()
    var mediaInfo = MediaInfo(url, title, artist, coverUrl, avatarUrl, live = isLive)

    if (dataJson !is JsonNull) {
      val item = (dataJson as JsonObject)["item"]?.jsonObject ?: throw InvalidExtractionParamsException("$url item not found")
      title = item["desc"]?.jsonPrimitive?.content ?: ""
      coverUrl = item["cover"]?.jsonPrimitive?.content ?: ""
      val userInfo = (dataJson as JsonObject)["user_info"]?.jsonObject
      artist = userInfo?.get("screen_name")?.jsonPrimitive?.content ?: ""
      avatarUrl = userInfo?.get("profile_image_url")?.jsonPrimitive?.content ?: ""

      if (isLive) {
        val streamData = item["stream_info"] ?: JsonNull

        val streamInfo = streamData.jsonObject
        val pull = streamInfo["pull"]?.jsonObject ?: throw InvalidExtractionParamsException("$url stream data pull section not found")

        val streamList = mutableListOf<StreamInfo>()
        val flvUrl = pull["live_origin_flv_url"]?.jsonPrimitive?.content ?: ""
        val hlsUrl = pull["live_origin_hls_url"]?.jsonPrimitive?.content ?: ""
        if (flvUrl.isNotEmpty()) {
          val originFlvUrl = flvUrl.substringBefore("_") + ".flv"
          streamList.add(StreamInfo(originFlvUrl, VideoFormat.flv, "origin", 0))
        }
        if (hlsUrl.isNotEmpty()) {
          val originHlsUrl = hlsUrl.substringBefore("_") + ".m3u8"
          streamList.add(StreamInfo(originHlsUrl, VideoFormat.hls, "origin", 0))
        }
        return mediaInfo.copy(streams = streamList, title = title, artist = artist, coverUrl = coverUrl, artistImageUrl = avatarUrl)
      }
      mediaInfo = mediaInfo.copy(title = title, artist = artist, coverUrl = coverUrl, artistImageUrl = avatarUrl)
    }
    return mediaInfo
  }
}