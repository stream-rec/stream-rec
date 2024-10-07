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
import github.hua0512.plugins.base.exceptions.InvalidExtractionParamsException
import github.hua0512.plugins.base.exceptions.InvalidExtractionResponseException
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.LIVE_DOUYIN_URL
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.WEBCAST_ENTER
import github.hua0512.utils.nonEmptyOrNull
import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 *
 * Douyin live stream extractor
 * @author hua0512
 * @date : 2024/3/16 13:10
 */
class DouyinExtractor(http: HttpClient, json: Json, override val url: String) : Extractor(http, json) {

  companion object {
    const val URL_REGEX = "(?:https?://)?(?:www\\.)?(?:live\\.)?douyin\\.com/([a-zA-Z0-9_\\.]+)"
  }

  override val regexPattern: Regex = URL_REGEX.toRegex()

  private lateinit var webRid: String

  private var jsonData: JsonElement = JsonNull

  init {
    platformHeaders[HttpHeaders.Referrer] = LIVE_DOUYIN_URL
    platformParams.fillDouyinCommonParams()
  }

  internal var idStr = ""

  override fun match(): Boolean {
    webRid = extractDouyinWebRid(url) ?: return false
    return true
  }

  override suspend fun isLive(): Boolean {
    // initialize cookies
    cookies = cookies.nonEmptyOrNull()?.let { populateDouyinCookieMissedParams(it, http) }
      ?: populateDouyinCookieMissedParams("", http)

    val response = getResponse(WEBCAST_ENTER) {
      timeout {
        requestTimeoutMillis = 15000
      }
      fillWebRid(webRid)
    }
    if (response.status != HttpStatusCode.OK) throw InvalidExtractionResponseException("$url failed, status code = ${response.status}")
    val textBody = response.bodyAsText()
    if (textBody.isEmpty()) {
      logger.info("$url response is empty")
      return false
    }
    jsonData = json.parseToJsonElement(textBody)
    val data = jsonData.jsonObject["data"]?.jsonObject ?: throw InvalidExtractionParamsException("$url failed to get data")

    val errorMsg = data.jsonObject["prompts"]?.jsonPrimitive?.content

    if (errorMsg != null) {
      logger.error("$url : $errorMsg")
      return false
    }

    val dataArray = data["data"]?.jsonArray

    if (dataArray == null || dataArray.isEmpty()) {
      logger.debug("$url unable to get live data")
      return false
    }

    idStr = data["enter_room_id"]?.jsonPrimitive?.content ?: run {
      logger.debug("$url unable to get id_str")
      return false
    }

    val liveData = dataArray[0].jsonObject

    val status = liveData["status"]?.jsonPrimitive?.int ?: run {
      logger.debug("$url unable to get live status")
      return false
    }
    return status == 2
  }

  override suspend fun extract(): MediaInfo {
    val isLive = isLive()

    if (jsonData is JsonNull) {
      logger.debug("$url unable to get json data")
      return MediaInfo(url, "", "", "", "")
    }

    val data = jsonData.jsonObject["data"]?.jsonObject?.get("data")?.jsonArray ?: return MediaInfo(url, "", "", "", "")

    val dataArray = data.jsonArray
    if (dataArray.isEmpty()) {
      logger.debug("$url unable to get live data")
      return MediaInfo(url, "", "", "", "")
    }

    val liveData = dataArray[0].jsonObject
    val title = liveData["title"]!!.jsonPrimitive.content
    val owner = liveData["owner"]
    val nickname = owner?.jsonObject?.get("nickname")?.jsonPrimitive?.content ?: ""

    val mediaInfo = MediaInfo(LIVE_DOUYIN_URL, title, nickname, "", "", isLive)
    // if not live, return basic media info
    if (!isLive) return mediaInfo

    // avatar is not available when not live
    val avatar =
      owner?.jsonObject?.get("avatar_thumb")?.jsonObject?.get("url_list")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content
        ?: run {
          logger.debug("$url unable to get avatar")
          ""
        }

    val cover = liveData["cover"]!!.jsonObject["url_list"]?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content ?: run {
      logger.debug("$url unable to get cover")
      ""
    }

    val liveCoreSdkData = liveData["stream_url"]!!.jsonObject["live_core_sdk_data"]?.jsonObject ?: run {
      logger.debug("$url unable to get live core sdk data")
      return mediaInfo.copy(coverUrl = cover)
    }

    // check if pull_datas is available (double screen streams)
    val pullDatas = liveData["stream_url"]!!.jsonObject["pull_datas"]?.jsonObject

    val pullData = if (pullDatas != null && pullDatas.isNotEmpty()) {
      // use the first pull_data
      pullDatas.entries.first().value.jsonObject
    } else {
      liveCoreSdkData["pull_data"]?.jsonObject ?: run {
        logger.debug("$url unable to get stream data")
        return mediaInfo.copy(coverUrl = cover)
      }
    }

    val qualities = pullData["options"]!!.jsonObject["qualities"]?.jsonArray?.map {
      val current = it.jsonObject
      val name = current["name"]!!.jsonPrimitive.content
      val sdkKey = current["sdk_key"]!!.jsonPrimitive.content
      val bitrate = current["v_bit_rate"]!!.jsonPrimitive.int
      Triple(sdkKey, name, bitrate)
    }

    val streamDataJson = pullData["stream_data"]?.jsonPrimitive?.content
      ?: throw InvalidExtractionParamsException("$url failed to get stream data json")

    val streamsData =
      json.parseToJsonElement(streamDataJson).jsonObject["data"]?.jsonObject
        ?: throw InvalidExtractionParamsException("$url failed to get stream data")

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
}
