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

import com.github.michaelbull.result.*
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.base.ExtractorError.*
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.LIVE_DOUYIN_URL
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.WEBCAST_ENTER
import github.hua0512.utils.nonEmptyOrNull
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.collections.set


internal class FallbackToDouyinMobileException : UnsupportedOperationException("Douyin pc api failed!")

/**
 *
 * Douyin live stream extractor
 * @author hua0512
 * @date : 2024/3/16 13:10
 */
open class DouyinExtractor(http: HttpClient, json: Json, override val url: String) : Extractor(http, json) {

  companion object {
    internal const val URL_REGEX = "(?:https?://)?(?:www\\.)?(?:v|live\\.)?douyin\\.com/([a-zA-Z0-9_\\.]+)"

    private const val DELETED_NICKNAME = "账号已注销"
  }

  override val regexPattern: Regex = URL_REGEX.toRegex()
  protected lateinit var webRid: String
  protected lateinit var secRid: String

  protected var liveData: JsonElement = JsonNull

  init {
    platformHeaders[HttpHeaders.Referrer] = LIVE_DOUYIN_URL
    platformParams.fillDouyinCommonParams()
  }

  internal var idStr = ""

  override fun match(): Result<String, ExtractorError> = extractDouyinWebRid(url).andThen {
    webRid = it
    Ok(webRid)
  }

  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    // initialize cookies
    cookies = cookies.nonEmptyOrNull()?.let { populateDouyinCookieMissedParams(it, http) }
      ?: populateDouyinCookieMissedParams("", http)

    val result = getResponse(WEBCAST_ENTER) {
      timeout {
        requestTimeoutMillis = 15000
      }
      fillWebRid(webRid)
      // find msToken from cookies
      val msToken = parseCookies(cookies)["msToken"]
      if (msToken != null) {
        parameter("msToken", msToken)
      }
    }

    if (result.isErr) {
      return result.asErr()
    }

    // response is successful
    val response = result.value
    val textBody = response.bodyAsText()

    val dataInfo = json.parseToJsonElement(textBody).jsonObject["data"]?.jsonObject
      ?: return Err(InvalidResponse("Failed to get data section"))

    val errorMsg = dataInfo.jsonObject["prompts"]?.jsonPrimitive?.content
    if (errorMsg != null) {
      logger.error("$url : $errorMsg")
      return Ok(false)
    }

    val user = dataInfo["user"]?.jsonObject
      ?: return Err(InvalidResponse("failed to get user section"))
    secRid = user["sec_uid"]?.jsonPrimitive?.content
      ?: return Err(InvalidResponse("failed to get sec_uid"))
    idStr = dataInfo["enter_room_id"]?.jsonPrimitive?.content ?: run {
      logger.debug("$url unable to get id_str")
      return Err(InvalidResponse("failed to get id_str"))
    }

    val nickname = user["nickname"]?.jsonPrimitive?.content ?: ""
    val avatars = user["avatar_thumb"]?.jsonObject?.get("url_list")?.jsonArray ?: emptyList()
    val isDeleted = nickname == DELETED_NICKNAME && (avatars.firstOrNull { it.jsonPrimitive.content.contains("aweme_default_avatar.png") } != null)

    if (isDeleted) {
      logger.error("$url account has been deleted")
      return Err(StreamerNotFound)
    }

    // check if data["data"] section is present
    // if not, throw FallbackToDouyinMobileException
    if (!dataInfo.containsKey("data") || dataInfo["data"]!! !is JsonArray || dataInfo["data"]!!.jsonArray.isEmpty()) {
      liveData = JsonNull
      logger.debug("$url pc api failed, response: {}", textBody)
      return Err(FallbackError(FallbackToDouyinMobileException()))
    }

    val dataArray = dataInfo["data"]!!.jsonArray
    liveData = dataArray.first().jsonObject

    val status = (liveData as JsonObject)["status"]?.jsonPrimitive?.int ?: run {
      logger.debug("$url unable to get live status")
      return Err(InvalidResponse("failed to get live status"))
    }

    return Ok(status == 2)
  }

  override suspend fun extract(): Result<MediaInfo, ExtractorError> {
    val isLive = isLive()

    if (isLive.isErr) return isLive.asErr()

    if (liveData is JsonNull) {
      logger.debug("$url unable to get json data")
      return Ok(MediaInfo(url, "", "", "", ""))
    }

    val liveData = liveData as JsonObject
    val title = liveData["title"]!!.jsonPrimitive.content
    val owner = liveData["owner"]
    val nickname = owner?.jsonObject?.get("nickname")?.jsonPrimitive?.content ?: ""

    val mediaInfo = MediaInfo(LIVE_DOUYIN_URL, title, nickname, "", "", isLive.value)
    // if not live, return basic media info
    if (!isLive.value) return Ok(mediaInfo)

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
      return Ok(mediaInfo.copy(coverUrl = cover))
    }

    // check if pull_datas is available (double screen streams)
    val pullDatas = liveData["stream_url"]!!.jsonObject["pull_datas"]?.jsonObject

    val pullData = if (!pullDatas.isNullOrEmpty()) {
      // use the first pull_data
      pullDatas.entries.first().value.jsonObject
    } else {
      liveCoreSdkData["pull_data"]?.jsonObject ?: run {
        logger.debug("$url unable to get stream data")
        return Err(NoStreamsFound)
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
      ?: return Err(InvalidResponse("failed to get stream data json"))

    val streamsData =
      json.parseToJsonElement(streamDataJson).jsonObject["data"]?.jsonObject
        ?: return Err(InvalidResponse("failed to get stream data"))

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

    return Ok(mediaInfo.copy(artistImageUrl = avatar, coverUrl = cover, streams = streams))
  }
}
