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

package github.hua0512.plugins.huya.download

import com.github.michaelbull.result.*
import github.hua0512.data.media.MediaInfo
import github.hua0512.plugins.base.ExtractorError
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

    private const val STREAMER_NOT_FOUND = "该主播不存在！"
  }

  override val regexPattern = URL_REGEX.toRegex()

  private lateinit var dataJson: JsonObject

  override fun match(): Result<String, ExtractorError.InvalidExtractionUrl> {
    val result = super.match()

    // check if the room id is numeric
    if (!roomId.matches(Regex("\\d+"))) {
      return Err(ExtractorError.InvalidExtractionUrl)
    }

    return result
  }

  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    val result = getResponse(MP_BASE_URL) {
      timeout {
        requestTimeoutMillis = 15000
      }
      contentType(ContentType.Application.Json)
      parameter("do", "profileRoom")
      parameter("m", "Live")
      parameter("roomid", roomId)
      parameter("showSecret", "1")
      userAgent(IPHONE_WX_UA)
    }

    if (result.isErr) {
      return result.asErr()
    }

    val response = result.value

    runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }
      .mapError { ExtractorError.InvalidResponse(it.message ?: "") }
      .andThen {
        dataJson = it
        Ok(it)
      }
      .onFailure {
        return Err(ExtractorError.InvalidResponse(it.message))
      }

    val status = dataJson["status"]?.jsonPrimitive?.int
    val message = dataJson["message"]?.jsonPrimitive?.content
    if (status != 200) {
      if (status == 422 && message == STREAMER_NOT_FOUND) {
        return Err(ExtractorError.StreamerNotFound)
      }
      return Err(ExtractorError.InvalidResponse("status: $status, message: $message"))
    }
    val data = dataJson["data"]?.jsonObject ?: return Err(ExtractorError.InvalidResponse("data is null from $url"))
    val realRoomStatus = data["realLiveStatus"]?.jsonPrimitive?.content ?: "OFF"
    val liveStatus = data["liveStatus"]?.jsonPrimitive?.content ?: "OFF"

    val liveData = data["liveData"] as? JsonObject
    liveData?.let {
      val intro = it["introduction"]?.jsonPrimitive?.content ?: ""
      if (intro.startsWith("【回放】")) {
        return Ok(false)
      }
    }

    val isLive = realRoomStatus == "ON" && liveStatus == "ON"
    return Ok(isLive)
  }

  override suspend fun extract(): Result<MediaInfo, ExtractorError> {
    // validate cookies
    validateCookie()

    val liveResult = isLive()

    if (liveResult.isErr) return liveResult.asErr()
    val isLive = liveResult.value

    val data = dataJson["data"]?.jsonObject!!
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
      return Ok(mediaInfo)
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
    if (!isLive) return Ok(mediaInfo)

    val gid = livedata?.get("gid")?.jsonPrimitive?.int ?: 0
    checkShouldSkipQuery(gid)

    // get stream info
    val streamJson = data["stream"]

    if (streamJson == null || streamJson is JsonNull) {
      return Err(ExtractorError.InvalidResponse("stream is null from $url"))
    } else if (streamJson is JsonArray && streamJson.isEmpty()) {
      return Err(ExtractorError.InvalidResponse("stream is empty from $url"))
    }

    if (streamJson !is JsonObject) {
      return Err(ExtractorError.InvalidResponse("stream is not a JsonObject from $url"))
    }

    val baseStreamInfoList =
      streamJson["baseSteamInfoList"]?.jsonArray?.ifEmpty { return Err(ExtractorError.InvalidResponse("baseSteamInfoList is empty from $url")) }
        ?: return Err(ExtractorError.InvalidResponse("baseSteamInfoList is null from $url"))

    // get bitrate list
    val bitrateInfo = livedata?.get("bitRateInfo")?.jsonPrimitive?.content?.run {
      json.parseToJsonElement(this).jsonArray
    } ?: streamJson["flv"]?.jsonObject?.get("rateArray")?.jsonArray
    ?: return Err(ExtractorError.InvalidResponse("bitRateInfo is null from $url"))

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
    mediaInfo = mediaInfo.copy(streams = streams)
    return Ok(mediaInfo)
  }

}