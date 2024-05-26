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

package github.hua0512.plugins.pandatv.download

import github.hua0512.data.media.MediaInfo
import github.hua0512.plugins.base.Extractor
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Pandalive live stream extractor.
 * @author hua0512
 * @date : 2024/5/8 21:47
 */
class PandaTvExtractor(http: HttpClient, json: Json, override val url: String) : Extractor(http, json) {

  companion object {
    internal const val URL = "https://www.pandalive.co.kr"
    internal const val URL_REGEX = """https?://(?:www\.)?pandalive\.co\.kr/live/play/([^/]+)"""
    private const val ID_REGEX = """routePath:\s*["'](\\u002F|/)live(\\u002F|/)play(\\u002F|/)(.+?)["']"""
    private const val LIVE_API = "https://api.pandalive.co.kr/v1/live/play"
  }

  override val regexPattern: Regex = URL_REGEX.toRegex()

  lateinit var id: String
  lateinit var userIdx: String
  lateinit var token: String
  lateinit var response: JsonObject

  init {
    platformHeaders[HttpHeaders.Referrer] = URL
  }

  override suspend fun isLive(): Boolean {
//    val response = getResponse(url)
//    if (response.status != HttpStatusCode.OK) {
//      return false
//    }
//    val body = response.bodyAsText()
//    // extract the id from the url
//    id = ID_REGEX.toRegex().find(body)?.groups?.get(4)?.value ?: ""
//
//    val roomPwd = ""

    id = regexPattern.find(url)?.groupValues?.get(1) ?: throw IllegalArgumentException("Failed to extract id from Pandalive url, url: $url")
    val liveResponse = postResponse(LIVE_API) {
      // form encoded body
      contentType(ContentType.Application.FormUrlEncoded)
      setBody(FormDataContent(
        Parameters.build {
          append("userId", id)
          append("action", "watch")
        }
      ))
    }
    if (liveResponse.status != HttpStatusCode.OK) {
      return false
    }
    // check if the stream is live
    response = liveResponse.body<JsonObject>()
    // check if result is present
    val result = response["result"] as? JsonPrimitive ?: return false
    // check if the stream is live
    val media = response["media"] as? JsonObject ?: return false
    val live = media["isLive"]?.jsonPrimitive?.booleanOrNull ?: false
    val isPwd = media["isPw"]?.jsonPrimitive?.booleanOrNull ?: false
    if (live && isPwd) {
      logger.error("$url is password protected")
      return false
    }
    return live
  }

  override suspend fun extract(): MediaInfo {
    val live = isLive()
    val mediaInfo = MediaInfo(URL, title = "", "", "", "", live = live)
    if (!live) return mediaInfo

    val media = response["media"] as JsonObject
    token = response["token"]?.jsonPrimitive?.content ?: ""
    userIdx = media["userIdx"]?.jsonPrimitive?.content ?: ""
    val title = media["title"]?.jsonPrimitive?.content ?: ""
    val artist = media["userId"]?.jsonPrimitive?.content ?: ""
    val thumbUrl = media["thumbUrl"]?.jsonPrimitive?.content ?: ""
    val userImg = media["userImg"]?.jsonPrimitive?.content ?: ""

    val playList = response["PlayList"] as JsonObject
    val hlsKeys = arrayOf("hls", "hls2", "hls3")
    // get first available stream
    val hlsUrl = hlsKeys.firstNotNullOfOrNull { key ->
      playList[key]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
    } ?: throw IllegalArgumentException("Failed to get hls stream url")

    // get hls stream info
    val hlsResponse = getResponse(hlsUrl)
    val streams = parseHlsPlaylist(hlsResponse)
    return mediaInfo.copy(title = title, artist = artist, coverUrl = thumbUrl, artistImageUrl = userImg, streams = streams)
  }
}