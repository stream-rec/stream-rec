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

package github.hua0512.plugins.douyu.download

import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import io.exoquery.pprint
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.set

/**
 * Douyu live stream extractor.
 * @author hua0512
 * @date : 2024/3/22 13:21
 */
open class DouyuExtractor(override val http: HttpClient, override val json: Json, override val url: String) : Extractor(http, json) {


  companion object {
    @JvmStatic
    internal val logger: Logger = LoggerFactory.getLogger(DouyuExtractor::class.java)
    internal const val DOUYU_URL = "https://www.douyu.com"

    private const val TITLE_REGEX = """Title-head\w*">([^<]+)<"""
    private const val ARTIST_REGEX = """Title-anchorName\w*" title="([^"]+)""""
    private const val LIVE_STATUS_REGEX = "\\\$ROOM\\.show_status\\s*=\\s*(\\d+);"
    private const val VIDEO_LOOP_REGEX = """"videoLoop":\s*(\d+)"""

    internal val midPatterns = listOf(
      """\${'$'}ROOM\.room_id\s*=\s*(\d+)""",
      """room_id\s*=\s*(\d+)""",
      """"room_id.?":(\d+)""",
      """data-onlineid=(\d+)""",
      """(房间已被关闭)"""
    )
  }

  // DOUYU rid
  internal lateinit var rid: String

  // DOUYU user preferred cdn
  internal var selectedCdn: String = ""
  private lateinit var htmlText: String

  init {
    platformHeaders[HttpHeaders.Origin] = DOUYU_URL
    platformHeaders[HttpHeaders.Referrer] = DOUYU_URL
  }

  override val regexPattern: Regex = Regex("""^https:\/\/www\.douyu\.com.*""")


  override suspend fun prepare() {
    super.prepare()
    // initialize MD5 js encryption
    getMd5Crypt(http)
  }

  override suspend fun isLive(): Boolean {
    val response = getResponse(url)
    if (response.status != HttpStatusCode.OK) {
      throw IllegalStateException("$url failed to get html")
    }
    htmlText = response.bodyAsText()
    logger.trace("{}", htmlText)
    for (pattern in midPatterns) {
      val matchResult = Regex(pattern).find(htmlText)
      if (matchResult != null) {
        rid = matchResult.groupValues[1]
        break
      }
    }
    logger.debug("$url mid: $rid")
    if (rid == "房间已被关闭") return false
    // check if the stream is live
    val liveStatus = LIVE_STATUS_REGEX.toRegex().find(htmlText)?.groupValues?.get(1)?.toInt()
    // check if the stream is a video loop
    val videoLoop = VIDEO_LOOP_REGEX.toRegex().find(htmlText)?.groupValues?.get(1)?.toInt()
    return liveStatus == 1 && videoLoop == 0
  }

  override suspend fun extract(): MediaInfo {
    val isLive = isLive()

    var title = Regex(TITLE_REGEX).find(htmlText)?.groupValues?.get(1) ?: ""
    var artist = Regex(ARTIST_REGEX).find(htmlText)?.groupValues?.get(1) ?: ""
    var avatar = ""
    var cover = ""
    val mediaInfo = MediaInfo(DOUYU_URL, title, artist, cover, avatar, live = isLive)

    if (!isLive) return mediaInfo

    val dataResponse = getResponse("https://open.douyucdn.cn/api/RoomApi/room/$rid")
    if (dataResponse.status != HttpStatusCode.OK) {
      logger.error("$url failed to get room data")
    } else {
      val jsonText = json.parseToJsonElement(dataResponse.bodyAsText())
      logger.debug("{}", jsonText)
      val errorCode = jsonText.jsonObject["error"]?.jsonPrimitive?.intOrNull ?: -1
      val data = run {
        if (errorCode == 0) {
          jsonText.jsonObject["data"]?.jsonObject
        } else {
          null
        }
      }
      data?.let {
        title = it["room_name"]?.jsonPrimitive?.content ?: title
        artist = it["owner_name"]?.jsonPrimitive?.content ?: artist
        avatar = it["avatar"]?.jsonPrimitive?.content ?: ""
        cover = it["room_thumb"]?.jsonPrimitive?.content ?: ""
      } ?: logger.error("$url failed to get room data")
    }


    val jsEnc = http.getDouyuH5Enc(json, htmlText, rid)
    logger.trace("jsEnc: $jsEnc")
    val paramsMap = withContext(Dispatchers.Default) { ub98484234(jsEnc, rid) }
    val streams = mutableListOf<StreamInfo>()

    val (streamInfo, multirates) = getStreamInfo(selectedCdn = selectedCdn, encMap = paramsMap)
    streams.add(streamInfo)

    // get the rest of the stream info
    // exclude the first one
    for (i in 1 until multirates.size) {
      val rateInfo = getRateInfo(multirates[i].jsonObject)
      val (stream, _) = getStreamInfo(selectedCdn = selectedCdn, selectedRate = rateInfo["rate"]!!, encMap = paramsMap)
      streams.add(stream)
    }
    logger.trace("$url streams: {}", pprint(streams))
    return mediaInfo.copy(title = title, artist = artist, artistImageUrl = avatar, coverUrl = cover, streams = streams)
  }

  private suspend fun getStreamInfo(selectedCdn: String = "", selectedRate: String = "0", encMap: Map<String, Any?>): Pair<StreamInfo, JsonArray> {
    val liveDataResponse = postResponse("https://www.douyu.com/lapi/live/getH5Play/$rid") {
      //  tctc-h5（备用线路4）, tct-h5（备用线路5）, ali-h5（备用线路6）, hw-h5（备用线路7）, hs-h5（备用线路13）
      parameter("cdn", selectedCdn)
      parameter("rate", selectedRate)
      parameter("iar", "0")
      parameter("ive", "0")
      encMap.forEach { (key, value) ->
        if (value != null)
          parameter(key, value.toString())
      }
    }
    if (liveDataResponse.status != HttpStatusCode.OK) {
      throw IllegalStateException("$url failed to get live data")
    }

    val liveDataJson = json.parseToJsonElement(liveDataResponse.bodyAsText())

    logger.debug("{}", liveDataJson)
    val error = liveDataJson.jsonObject["error"]?.jsonPrimitive?.intOrNull ?: -1

    if (error != 0) {
      val msg = liveDataJson.jsonObject["msg"]?.jsonPrimitive?.content
      throw IllegalStateException("$url failed to get live data, error code $error, msg: $msg")
    }

    // current live data, should be the first one, with highest bitrate and quality
    val liveData = liveDataJson.jsonObject["data"]?.jsonObject!!
    // url = rtmp_url + rtmp_live
    val url = liveData["rtmp_url"]!!.jsonPrimitive.content + "/" + liveData["rtmp_live"]!!.jsonPrimitive.content
    // list of supported rates
    val multirates = liveData["multirates"]?.jsonArray
    // rate should be 0, but we obtain it from the live data just in case
    val rate = liveData["rate"]?.jsonPrimitive?.intOrNull
    // cdn, usually tct-h5
    val cdn = liveData["rtmp_cdn"]!!.jsonPrimitive.content
    val qualityName = getRateInfo(multirates!!.find { it.jsonObject["rate"]!!.jsonPrimitive.intOrNull === rate }!!.jsonObject)
    return StreamInfo(
      url = url,
      format = VideoFormat.flv,
      quality = qualityName["name"]!!,
      bitrate = qualityName["bitrate"]!!.toLong(),
      priority = qualityName["highBit"]!!.toInt(),
      frameRate = 0.0,
      extras = mapOf("cdn" to cdn, "rate" to qualityName["rate"]!!)
    ) to multirates
  }

  private fun getRateInfo(info: JsonObject): Map<String, String> {
    val rate = info["rate"]!!.jsonPrimitive.int
    val bit = info["bit"]!!.jsonPrimitive.int
    val name = info["name"]!!.jsonPrimitive.content
    val highBit = info["highBit"]!!.jsonPrimitive.content
    return mapOf(
      "name" to name,
      "highBit" to highBit,
      "rate" to rate.toString(),
      "bitrate" to bit.toString()
    )
  }

}