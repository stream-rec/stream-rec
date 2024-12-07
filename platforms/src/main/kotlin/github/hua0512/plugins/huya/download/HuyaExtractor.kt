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

import com.github.michaelbull.result.*
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.utils.decodeBase64
import github.hua0512.utils.md5
import github.hua0512.utils.nonEmptyOrNull
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * Huya live stream extractor
 * @author hua0512
 * @date : 2024/3/15 19:46
 */
open class HuyaExtractor(override val http: HttpClient, override val json: Json, override val url: String) :
  Extractor(http, json) {
  companion object {
    const val BASE_URL = "https://www.huya.com"
    const val COOKIE_URL = "https://udblgn.huya.com/web/cookie/verify"
    const val URL_REGEX = "(?:https?://)?(?:(?:www|m)\\.)?huya\\.com/([a-zA-Z0-9]+)"
    const val ROOM_DATA_REGEX = "var TT_ROOM_DATA = (.*?);"
    const val AVATAR_REGEX = """avatar"\s*:\s*"([^"]+)"""
    const val STATE_REGEX = """"state"\s*:\s*"(\w+)""""
    const val LIVE_CHANNEL_REGEX = """"liveChannel":"([^"]+)""""
    const val STREAM_REGEX = "stream: (\\{.+)\\n.*?};"
    const val NICK_REGEX = """nick"\s*:\s*"([^"]+)"""
    const val INTRODUCTION_REGEX = """introduction"\s*:\s*"([^"]+)"""
    const val SCREENSHOT_REGEX = """screenshot"\s*:\s*"([^"]+)"""
    const val AYYUID_REGEX = "yyid\":\"?(\\d+)\"?"
    const val TOPSID_REGEX = "lChannelId\":\"?(\\d+)\"?"
    const val SUBID_REGEX = "lSubChannelId\":\"?(\\d+)\"?"
    const val PRESENTER_UID_REGEX = "lPresenterUid\":\"?(\\d+)\"?"
    const val UID_REGEX = "uid\":\"?(\\d+)\"?"
    const val GID_REGEX = "gid\":\"?(\\d+)\"?"

    internal const val IPHONE_WX_UA =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.49(0x18003137) NetType/WIFI Language/zh_CN WeChat/8.0.49.33 CFNetwork/1474 Darwin/23.0.0"


    internal val requestHeaders = arrayOf(
      HttpHeaders.Origin to BASE_URL,
      HttpHeaders.Referrer to BASE_URL
    )

    private const val APP_ID = 5002
    private const val WEB_PLATFORM_ID = 100
  }

  override val regexPattern = URL_REGEX.toRegex()
  protected var roomId: String = ""
  private lateinit var htmlResponseBody: String
  private val ayyuidPattern = AYYUID_REGEX.toRegex()
  private val topsidPattern = TOPSID_REGEX.toRegex()
  private val subidPattern = SUBID_REGEX.toRegex()
  private val presenterUidPattern = PRESENTER_UID_REGEX.toRegex()

  protected var shouldSkipQueryBuild = false

  //  internal var ayyuid: Long = 0
//  internal var topsid: Long = 0
//  internal var subid: Long = 0
  internal var presenterUid: Long = 0
  internal var userId = 0L
  private var isCookieVerified = false
  var forceOrigin = false
  var hasErrorOcurred = false


  init {
    requestHeaders.forEach {
      platformHeaders[it.first] = it.second
    }
  }

  override fun match() =
    regexPattern.find(url)?.groupValues?.last()
      ?.takeIf { it.isNotEmpty() }
      ?.also { rid -> roomId = rid }
      ?.let { Ok(it) }
      ?: Err(ExtractorError.InvalidExtractionUrl)

  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    val apiResult = getResponse("$BASE_URL/$roomId") {
      timeout {
        requestTimeoutMillis = 15000
      }
    }
    if (apiResult.isErr) return apiResult.asOk()

    val response = apiResult.value

    htmlResponseBody = response.bodyAsText().apply {
      val uid = UID_REGEX.toRegex().find(this)?.groupValues?.get(1)?.toLongOrNull()
      if (uid == 0L || contains("找不到这个主播")) {
        return Err(ExtractorError.StreamerNotFound)
      }
      if (contains("该主播涉嫌违规，正在整改中")) {
        return Err(ExtractorError.StreamerBanned)
      }
    }

//    ayyuid = ayyuidPattern.find(htmlResponseBody)?.groupValues?.get(1)?.toLong() ?: 0
//    topsid = topsidPattern.find(htmlResponseBody)?.groupValues?.get(1)?.toLong() ?: 0
//    subid = subidPattern.find(htmlResponseBody)?.groupValues?.get(1)?.toLong() ?: 0
    presenterUid = presenterUidPattern.find(htmlResponseBody)?.groupValues?.get(1)?.toLong() ?: 0


    val matchResult = ROOM_DATA_REGEX.toRegex().find(htmlResponseBody).toResultOr {
      ExtractorError.InvalidResponse("TT_ROOM_DATA not found")
    }.toErrorIf({ it.value.isEmpty() }) {
      ExtractorError.InvalidResponse("TT_ROOM_DATA is empty")
    }

    if (matchResult.isErr) return matchResult.asErr()
    val stateRegex = STATE_REGEX.toRegex()

    return stateRegex.find(matchResult.value.groupValues[1])?.groupValues?.get(1).toResultOr {
      ExtractorError.InvalidResponse("state not found")
    }.andThen {
      Ok(it == "ON")
    }
  }

  override suspend fun extract(): Result<MediaInfo, ExtractorError> {
    // validate cookie
    validateCookie()

    // get live status
    val liveResult = isLive()

    if (liveResult.isErr) {
      return liveResult.asErr()
    }

    // get media info from htmlResponseBody
    val avatarUrl = AVATAR_REGEX.toRegex().find(htmlResponseBody)?.groupValues?.get(1) ?: "".also {
      logger.debug("failed to extract avatar from $url")
    }

    // extract nick
    val streamerName = NICK_REGEX.toRegex().find(htmlResponseBody)?.groupValues?.get(1) ?: "".also {
      logger.debug("failed to extract nick from $url")
    }
    // extract title
    val streamTitle = INTRODUCTION_REGEX.toRegex().find(htmlResponseBody)?.groupValues?.get(1) ?: "".also {
      logger.debug("failed to extract introduction from $url")
    }
    // extract cover screenshot
    val coverUrl = SCREENSHOT_REGEX.toRegex().find(htmlResponseBody)?.groupValues?.get(1) ?: "".also {
      logger.debug("failed to extract screenshot from $url")
    }

    val live = liveResult.value
    val mediaInfo = MediaInfo(
      site = BASE_URL,
      title = streamTitle,
      artist = streamerName,
      coverUrl = coverUrl,
      artistImageUrl = avatarUrl,
      live = live,
    )

    // if not live, return basic media info
    if (!live) return Ok(mediaInfo)

    val gid = GID_REGEX.toRegex().find(htmlResponseBody)?.groupValues?.get(1)?.toInt() ?: 0

    checkShouldSkipQuery(gid)


    val parseStreamJsonResult = STREAM_REGEX.toRegex().find(htmlResponseBody)?.groupValues?.get(1).toResultOr {
      ExtractorError.InvalidResponse("stream section not found")
    }.map {
      json.parseToJsonElement(it)
    }.toErrorUnless({ it is JsonObject && it.isNotEmpty() }) {
      ExtractorError.InvalidResponse("stream section failed to parse: $it")
    }.map {
      it.jsonObject
    }

    if (parseStreamJsonResult.isErr) {
      return parseStreamJsonResult.asErr()
    }

    val streamJson = parseStreamJsonResult.value

    val vMultiStreamInfo =
      streamJson["vMultiStreamInfo"] ?: return Err(ExtractorError.InvalidResponse("vMultiStreamInfo is null"))

    val data =
      streamJson["data"]?.jsonArray?.getOrNull(0)?.jsonObject
        ?: return Err(ExtractorError.InvalidResponse("data is null"))

    val gameLiveInfo =
      data["gameLiveInfo"]?.jsonObject ?: return Err(ExtractorError.InvalidResponse("gameLiveInfo is null"))


    val gameStreamInfoListResult = data["gameStreamInfoList"]?.jsonArray.toResultOr {
      ExtractorError.InvalidResponse("gameStreamInfoList is null")
    }.toErrorIf({ it.isEmpty() }) {
      ExtractorError.InvalidResponse("gameStreamInfoList is empty")
    }

    if (gameStreamInfoListResult.isErr) {
      return gameStreamInfoListResult.asErr()
    }

    val gameStreamInfoList = gameStreamInfoListResult.value

    // default bitrate
    val defaultBitrate = gameLiveInfo["bitRate"]?.jsonPrimitive?.int ?: 0
    // available bitrate list
    val bitrateList: List<Pair<Int, String>> = vMultiStreamInfo.jsonArray.mapIndexed { index, jsonElement ->
      var iBitrate = jsonElement.jsonObject["iBitRate"]?.jsonPrimitive?.int ?: 0
      // if iBitrate is 0, use default bitrate
      if (iBitrate == 0) iBitrate = defaultBitrate
      val bitrate = iBitrate
      val displayName = jsonElement.jsonObject["sDisplayName"]?.jsonPrimitive?.content ?: ""
      bitrate to displayName
    }
    val streams = extractLiveStreams(gameStreamInfoList, bitrateList, defaultBitrate)

    return Ok(mediaInfo.copy(streams = streams))
  }

  protected suspend fun extractLiveStreams(
    gameStreamInfoList: JsonArray,
    bitrateList: List<Pair<Int, String>>,
    maxBitRate: Int,
  ): MutableList<StreamInfo> {
    // build stream info
    val streams = mutableListOf<StreamInfo>()
    val time = Clock.System.now()

    withContext(Dispatchers.Default) {
      gameStreamInfoList.forEach { streamInfo ->
        if (userId == 0L) {
          // extract uid from cookies
          // if not found, use streamer's uid
          // if still not found, use random uid
          userId = cookies.nonEmptyOrNull()?.let {
            val cookie = parseClientCookiesHeader(cookies)
            cookie["yyuid"]?.toLongOrNull() ?: cookie["udb_uid"]?.toLongOrNull()
          } ?: streamInfo.jsonObject["lPresenterUid"]?.jsonPrimitive?.content?.toLongOrNull()
                  ?: (12340000L..12349999L).random()
        }
        val cdn = streamInfo.jsonObject["sCdnType"]?.jsonPrimitive?.content ?: ""

        val priority = streamInfo.jsonObject["iWebPriorityRate"]?.jsonPrimitive?.int ?: 0

        arrayOf(true, false).forEach buildLoop@{ isFlv ->
          val streamUrl = buildUrl(streamInfo, userId, time, null, isFlv).nonEmptyOrNull() ?: return@buildLoop
          bitrateList.forEach { (bitrate, displayName) ->
            // Skip HDR streams as they are not supported
            if (displayName.contains("HDR")) return@forEach

            val url = if (bitrate == maxBitRate) streamUrl else "$streamUrl&ratio=$bitrate"
            streams.add(
              StreamInfo(
                url = url,
                format = if (isFlv) VideoFormat.flv else VideoFormat.hls,
                quality = displayName,
                bitrate = bitrate.toLong(),
                priority = priority,
                frameRate = 0.0,
                extras = mapOf("cdn" to cdn)
              )
            )
          }
        }
      }
    }
    return streams
  }

  protected fun buildUrl(
    streamInfo: JsonElement,
    uid: Long,
    time: Instant,
    bitrate: Int? = null,
    isFlv: Boolean,
  ): String {
    val antiCode =
      streamInfo.jsonObject[if (isFlv) "sFlvAntiCode" else "sHlsAntiCode"]?.jsonPrimitive?.content ?: return ""
    var streamName = streamInfo.jsonObject["sStreamName"]?.jsonPrimitive?.content ?: return ""

    if (forceOrigin) {
      streamName = streamName.replace("-imgplus", "")
    }
    val url = streamInfo.jsonObject[if (isFlv) "sFlvUrl" else "sHlsUrl"]?.jsonPrimitive?.content ?: return ""
    val urlSuffix =
      streamInfo.jsonObject[if (isFlv) "sFlvUrlSuffix" else "sHlsUrlSuffix"]?.jsonPrimitive?.content ?: return ""

    /**
     * build anticode for game stream
     * "xingxiu" streamers should skip query generation
     * @see [checkShouldSkipQuery]
     */
    val queryParams = if (!shouldSkipQueryBuild) buildQuery(antiCode, uid, streamName, time, bitrate) else antiCode

    return "$url/$streamName.$urlSuffix?$queryParams"
  }

  private fun buildQuery(
    anticode: String,
    uid: Long,
    sStreamName: String,
    time: Instant,
    bitrate: Int? = null,
  ): String {
    val u = (uid shl 8 or (uid shr 24)) and -0x1
    val query = parseQueryString(anticode.removeSuffix(","))
    val wsTime = query["wsTime"]!!
    val ct = ((wsTime.toInt(16) + Random.nextDouble()) * 1000).toInt()
    val seqId = ct + uid
    val fm = query["fm"]?.decodeBase64()?.split("_")?.get(0)!!

    val platformId = query["t"]?.toInt() ?: WEB_PLATFORM_ID

    @Suppress("SpellCheckingInspection")
    val ctype = query["ctype"]!!
    val ss = "$seqId|${ctype}|$platformId".md5()
    val wsSecret = "${fm}_${u}_${sStreamName}_${ss}_${wsTime}".md5()

    val parameters = ParametersBuilder().apply {
      append("wsSecret", wsSecret)
      append("wsTime", wsTime)
      append("seqid", seqId.toString())
      append("ctype", ctype)
      append("fs", query["fs"]!!)
      append("u", u.toString())
      append("t", platformId.toString())
      append("ver", "1")
      append("uuid", ((ct % 1e10 + Random.nextDouble()) * 1e3 % 0xffffffff).toInt().toString())
      // fixed 264 codec
      // huya 265 codec is fake 265
      append("sdk_sid", time.epochSeconds.toString())
      append("codec", "264")
      bitrate?.let { append("ratio", it.toString()) }
    }

    return parameters.build().formUrlEncode()
  }

  /**
   * Validate cookie if present
   * @return true if cookie is valid, false otherwise
   */
  protected suspend fun validateCookie(): Boolean {
    // check if cookie is present
    if (cookies.isNotEmpty()) {
      // verify cookie
      isCookieVerified = verifyCookie().getOr(false)
      if (!isCookieVerified) {
        logger.warn("$url failed to verify cookie")
      }
    } else {
      isCookieVerified = true
    }
    return isCookieVerified
  }


  private suspend fun verifyCookie(): Result<Boolean, ExtractorError> {
    if (isCookieVerified) return Ok(true)

    // make verify cookie request
    val result = postResponse(COOKIE_URL) {
      timeout {
        requestTimeoutMillis = 15000
      }
      // set json body
      contentType(ContentType.Application.Json)
      setBody(
        buildJsonObject {
          put("appId", APP_ID)
        }
      )
    }
    if (result.isErr) {
      return result.asErr()
    }
    val response = result.value

    val body = response.bodyAsText()
    val jsonResult = runCatching {
      json.parseToJsonElement(body).takeIf { it is JsonObject }?.jsonObject
        ?: throw IllegalStateException("Invalid json")
    }.mapError {
      ExtractorError.InvalidResponse("Invalid cookies json response")
    }

    if (jsonResult.isErr) {
      return jsonResult.asErr()
    }
    val json = jsonResult.value

    val returnCode = json["returnCode"]?.jsonPrimitive?.int ?: 0
    return Ok(returnCode == 0)
  }


  protected fun checkShouldSkipQuery(gid: Int) {
    // 2024-11-12 skip query param build for "xingxiu" areas
    // use default query params instead of calculated
    logger.debug("$url gid: $gid")
    if (hasErrorOcurred) return
    shouldSkipQueryBuild = gid == 1663
  }

  override fun onRepeatedError(error: ExtractorError, retries: Int) {
    logger.error("$url error: $error, retries: $retries")
    if (error is ExtractorError.ApiError) {
      val is403Error = error.throwable.message?.contains("403")
      if (is403Error == true) {
        logger.error("$url stream 403 error detected, retrying with skip query=${!shouldSkipQueryBuild}")
        hasErrorOcurred = true
        shouldSkipQueryBuild = !shouldSkipQueryBuild
      }
    }
  }

}