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

package github.hua0512.plugins.huya.download

import github.hua0512.data.media.MediaInfo
import github.hua0512.plugins.base.exceptions.InvalidExtractionParamsException
import github.hua0512.plugins.base.exceptions.InvalidExtractionResponseException
import github.hua0512.plugins.base.exceptions.InvalidExtractionUrlException
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Huya extractor v2
 *
 * @note Only supports numeric room ids
 * @author hua0512
 * @date : 2024/5/21 20:20
 */
class HuyaExtractorV2(override val http: HttpClient, override val json: Json, override val url: String) :
  HuyaExtractor(http, json, url) {

  companion object {
    private const val MP_BASE_URL = "https://mp.huya.com/cache.php"
  }

  override val regexPattern = URL_REGEX.toRegex()

  private lateinit var dataJson: JsonObject

  override fun match(): Boolean {
    val result = super.match()

    // check if the room id is numeric
    if (!roomId.matches(Regex("\\d+"))) {
      throw InvalidExtractionUrlException("This extractor only supports numeric room ids")
    }

    return result
  }

  override suspend fun isLive(): Boolean {
    val response = getResponse(MP_BASE_URL) {
      timeout {
        requestTimeoutMillis = 15000
      }
      contentType(ContentType.Application.Json)
      parameter("do", "profileRoom")
      parameter("m", "Live")
      parameter("roomid", roomId)
      parameter("showSecret", "1")
      url.parameters.remove(HttpHeaders.UserAgent)
      userAgent(IPHONE_WX_UA)
    }

    if (response.status != HttpStatusCode.OK) throw InvalidExtractionResponseException("Invalid response status ${response.status.value} from $url")

    dataJson = json.parseToJsonElement(response.bodyAsText()).jsonObject

    val status = dataJson["status"]?.jsonPrimitive?.int
    val message = dataJson["message"]?.jsonPrimitive?.content
    if (status != 200) {
      throw InvalidExtractionParamsException("Invalid status code $status from $url, message: $message")
    }
    val data = dataJson["data"]?.jsonObject ?: throw InvalidExtractionParamsException("data is null from $url")
    val realRoomStatus = data["realLiveStatus"]?.jsonPrimitive?.content ?: "OFF"
    val liveStatus = data["liveStatus"]?.jsonPrimitive?.content ?: "OFF"

    return realRoomStatus == "ON" && liveStatus == "ON"
  }

  override suspend fun extract(): MediaInfo {
    // validate cookies
    validateCookie()

    val isLive = isLive()

    val data = dataJson["data"]?.jsonObject ?: throw InvalidExtractionParamsException("data is null from $url")
    val profileInfo = data.jsonObject["profileInfo"]?.jsonObject

    // get danmu properties
//    ayyuid = profileInfo?.get("yyid")?.jsonPrimitive?.long ?: 0
//    topsid = data["chTopId"]?.jsonPrimitive?.long ?: 0
//    subid = data["subChId"]?.jsonPrimitive?.long ?: 0
    presenterUid = profileInfo?.get("uid")?.jsonPrimitive?.long ?: 0
    // get avatar
    val avatarUrl = profileInfo?.get("avatar180")?.jsonPrimitive?.content ?: ""
    // get streamer name
    val streamerName = profileInfo?.get("nick")?.jsonPrimitive?.content ?: ""

    var mediaInfo = MediaInfo(
      site = BASE_URL,
      title = "",
      artist = streamerName,
      coverUrl = "",
      artistImageUrl = "",
      live = isLive,
    )

    var livedata = data["liveData"]

    // there is not livedata if not live or livestatus is FREEZE
    if (livedata is JsonNull) {
      return mediaInfo
    }

    livedata = livedata?.jsonObject

    // get stream title and cover
    val streamTitle = livedata?.get("introduction")?.jsonPrimitive?.content ?: ""
    val coverUrl = livedata?.get("screenshot")?.jsonPrimitive?.content ?: ""

    // update media info
    mediaInfo = mediaInfo.copy(
      title = streamTitle,
      coverUrl = coverUrl,
      artistImageUrl = avatarUrl
    )

    // if not live, return basic media info
    if (!isLive) return mediaInfo

    val gid = livedata?.get("gid")?.jsonPrimitive?.int ?: 0
    checkShouldSkipQuery(gid)

    // get stream info
    val streamJson = data["stream"]

    if (streamJson == null || streamJson is JsonNull) {
      throw InvalidExtractionParamsException("stream is null from $url")
    }

    if (streamJson is JsonArray && streamJson.isEmpty()) {
      throw InvalidExtractionParamsException("$url stream is empty")
    }

    if (streamJson !is JsonObject) {
      throw InvalidExtractionParamsException("stream is not a json object from $url : $streamJson")
    }

    val baseStreamInfoList =
      streamJson["baseSteamInfoList"]?.jsonArray
        ?: throw InvalidExtractionParamsException("baseStreamInfoList is null from $url")
    if (baseStreamInfoList.isEmpty()) {
      throw InvalidExtractionParamsException("baseStreamInfoList is empty from $url")
    }

    // get bitrate list
    val bitrateInfo = livedata?.get("bitRateInfo")?.jsonPrimitive?.content?.run {
      json.parseToJsonElement(this).jsonArray
    } ?: streamJson["flv"]?.jsonObject?.get("rateArray")?.jsonArray
    ?: throw InvalidExtractionParamsException("bitRateInfo is null from $url")

    val maxBitRate = livedata?.get("bitRate")?.jsonPrimitive?.int ?: 0

    // available bitrate list
    val bitrateList: List<Pair<Int, String>> = bitrateInfo.mapIndexed { index, jsonElement ->
      val bitrate = if (index == 0) maxBitRate else jsonElement.jsonObject["iBitRate"]?.jsonPrimitive?.int ?: 0
      val displayName = jsonElement.jsonObject["sDisplayName"]?.jsonPrimitive?.content ?: ""
      bitrate to displayName
    }

    // get additional qualities (HACK: exsphd)
    val anticode = baseStreamInfoList.first().jsonObject["sFlvAntiCode"]?.jsonPrimitive?.content
    val queries = parseQueryString(anticode ?: "")
    val additionalQualities = queries["exsphd"]?.let { exsphd ->
      val qualities = exsphd.removeSuffix(",").split(",")
      qualities.map {
        val quality = it.split("_")
        val codec = quality[0]
        val bitrate = quality[1].toInt()
        bitrate to codec
      }.filter {
        it.first > 10000
      }
    } ?: emptyList()

    // build stream info
    val streams = extractLiveStreams(baseStreamInfoList, additionalQualities + bitrateList, maxBitRate)
    return mediaInfo.copy(streams = streams)
  }

}