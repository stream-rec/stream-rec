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
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.utils.decodeBase64
import github.hua0512.utils.nonEmptyOrNull
import github.hua0512.utils.toMD5Hex
import github.hua0512.utils.withIOContext
import io.ktor.client.*
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
class HuyaExtractor(override val http: HttpClient, override val json: Json, override val url: String) :
  Extractor(http, json) {
  companion object {
    const val BASE_URL = "https://www.huya.com"
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


    internal val requestHeaders = arrayOf(
      HttpHeaders.Origin to BASE_URL,
      HttpHeaders.Referrer to BASE_URL
    )

    internal const val PLATFORM_ID = 100
  }

  override val regexPattern = URL_REGEX.toRegex()
  private var roomId: String = ""
  private lateinit var htmlResponseBody: String
  private val ayyuidPattern = AYYUID_REGEX.toRegex()
  private val topsidPattern = TOPSID_REGEX.toRegex()
  private val subidPattern = SUBID_REGEX.toRegex()

  internal var ayyuid: Long = 0
  internal var topsid: Long = 0
  internal var subid: Long = 0


  init {
    requestHeaders.forEach {
      platformHeaders[it.first] = it.second
    }
  }

  override fun match(): Boolean {
    roomId = try {
      val matchResult = regexPattern.find(url) ?: return false
      matchResult.groupValues.last()
    } catch (e: Exception) {
      throw IllegalArgumentException("Invalid url $url for $this, ${e.message}")
    }
    if (roomId.isEmpty()) {
      throw IllegalArgumentException("Unable to extract roomId from $url")
    }

    return true
  }

  override suspend fun isLive(): Boolean {
    val response: HttpResponse = getResponse("$BASE_URL/$roomId")
    if (response.status != HttpStatusCode.OK) throw IllegalStateException("Invalid response status ${response.status.value} from $url")

    htmlResponseBody = response.bodyAsText().apply {
      if (isEmpty()) {
        throw IllegalArgumentException("Empty response body from $url")
      }
      if (contains("找不到这个主播")) {
        throw IllegalArgumentException("invalid url, no such streamer")
      }
    }

    ayyuid = ayyuidPattern.find(htmlResponseBody)?.groupValues?.get(1)?.toLong() ?: 0
    topsid = topsidPattern.find(htmlResponseBody)?.groupValues?.get(1)?.toLong() ?: 0
    subid = subidPattern.find(htmlResponseBody)?.groupValues?.get(1)?.toLong() ?: 0

    val matchResult = ROOM_DATA_REGEX.toRegex().find(htmlResponseBody)?.also {
      if (it.value.isEmpty()) {
        throw IllegalStateException("Empty TT_ROOM_DATA from $url")
      }
    } ?: throw IllegalStateException("Unable to extract TT_ROOM_DATA from $url")

    val matchJson = matchResult.groupValues[1].apply {
      if (isEmpty()) {
        throw IllegalStateException("Empty TT_ROOM_DATA content from $url")
      }
    }
    val stateRegex = STATE_REGEX.toRegex()
    val state = stateRegex.find(matchJson)?.groupValues?.get(1) ?: ""
    if (state.isEmpty()) {
      throw IllegalStateException("Unable to extract state from $url")
    }
    return state == "ON"
  }

  override suspend fun extract(): MediaInfo {

    // get live status
    val isLive = withIOContext { isLive() }

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
    val mediaInfo = MediaInfo(
      site = BASE_URL,
      title = streamTitle,
      artist = streamerName,
      coverUrl = coverUrl,
      artistImageUrl = avatarUrl,
      live = isLive,
    )

    // if not live, return basic media info
    if (!isLive) return mediaInfo

    val streamRegex = STREAM_REGEX.toRegex().find(htmlResponseBody)?.groupValues?.get(1) ?: "".also {
      throw IllegalStateException("Unable to extract stream from $url")
    }

    val streamJson = json.parseToJsonElement(streamRegex).jsonObject

    val vMultiStreamInfo =
      streamJson["vMultiStreamInfo"] ?: throw IllegalStateException("$url vMultiStreamInfo is null")

    val data =
      streamJson["data"]?.jsonArray?.getOrNull(0)?.jsonObject ?: throw IllegalStateException("$url data is null")


    val gameLiveInfo = data["gameLiveInfo"]?.jsonObject ?: throw IllegalStateException("$url gameLiveInfo is null")


    val gameStreamInfoList = data["gameStreamInfoList"]?.jsonArray.run {
      if (isNullOrEmpty()) null
      else this
    } ?: throw IllegalStateException("$url gameStreamInfoList is null")

    // default max bit rate
    val maxBitRate = gameLiveInfo["bitRate"]?.jsonPrimitive?.int ?: 0
    // available bitrate list
    val bitrateList: List<Pair<Int, String>> = vMultiStreamInfo.jsonArray.mapIndexed { index, jsonElement ->
      val bitrate = if (index == 0) maxBitRate else jsonElement.jsonObject["iBitRate"]?.jsonPrimitive?.int ?: 0
      val displayName = jsonElement.jsonObject["sDisplayName"]?.jsonPrimitive?.content ?: ""
      bitrate to displayName
    }
    // build stream info
    val streams = mutableListOf<StreamInfo>()
    val time = Clock.System.now()

    withContext(Dispatchers.Default) {
      gameStreamInfoList.forEach { streamInfo ->
        val uid = streamInfo.jsonObject["lPresenterUid"]?.jsonPrimitive?.content?.toLongOrNull()
          ?: (12340000L..12349999L).random()
        val cdn = streamInfo.jsonObject["sCdnType"]?.jsonPrimitive?.content ?: ""

        val priority = streamInfo.jsonObject["iWebPriorityRate"]?.jsonPrimitive?.int ?: 0

        arrayOf(true, false).forEach buildLoop@{ isFlv ->
          val streamUrl = buildUrl(streamInfo, uid, time, null, isFlv).nonEmptyOrNull() ?: return@buildLoop
          bitrateList.forEach { (bitrate, displayName) ->
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
    return mediaInfo.copy(streams = streams)
  }

  private fun buildUrl(
    streamInfo: JsonElement,
    uid: Long,
    time: Instant,
    bitrate: Int? = null,
    isFlv: Boolean
  ): String {
    val antiCode =
      streamInfo.jsonObject[if (isFlv) "sFlvAntiCode" else "sHlsAntiCode"]?.jsonPrimitive?.content ?: return ""
    val streamName = streamInfo.jsonObject["sStreamName"]?.jsonPrimitive?.content ?: return ""
    val url = streamInfo.jsonObject[if (isFlv) "sFlvUrl" else "sHlsUrl"]?.jsonPrimitive?.content ?: return ""
    val urlSuffix =
      streamInfo.jsonObject[if (isFlv) "sFlvUrlSuffix" else "sHlsUrlSuffix"]?.jsonPrimitive?.content ?: return ""

    return "$url/$streamName.$urlSuffix" + "?" + buildQuery(antiCode, uid, streamName, time, bitrate)
  }

  private fun buildQuery(
    anticode: String,
    uid: Long,
    sStreamName: String,
    time: Instant,
    bitrate: Int? = null
  ): String {
    val u = (uid shl 8 or (uid shr 24)) and -0x1
    val query = parseQueryString(anticode.removeSuffix(","))
    val wsTime = query["wsTime"]!!
    val ct = ((wsTime.toInt(16) + Random.nextDouble()) * 1000).toInt()
    val seqId = ct + uid
    val fm = query["fm"]?.decodeBase64()?.split("_")?.get(0)!!

    @Suppress("SpellCheckingInspection")
    val ctype = query["ctype"]!!
    val ss = "$seqId|${ctype}|$PLATFORM_ID".toByteArray().toMD5Hex()
    val wsSecret = "${fm}_${u}_${sStreamName}_${ss}_${wsTime}".toByteArray().toMD5Hex()

    val parameters = ParametersBuilder().apply {
      append("wsSecret", wsSecret)
      append("wsTime", wsTime)
      append("seqid", seqId.toString())
      append("ctype", ctype)
      append("fs", query["fs"]!!)
      append("u", u.toString())
      append("t", PLATFORM_ID.toString())
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

}