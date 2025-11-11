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

package github.hua0512.plugins.douyu.download

import com.github.michaelbull.result.*
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.ExtractorError
import io.exoquery.kmp.pprint
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Douyu live stream extractor.
 * @author hua0512
 * @date : 2024/3/22 13:21
 */
open class DouyuExtractor(override val http: HttpClient, override val json: Json, override val url: String) :
  Extractor(http, json) {


  companion object {

    init {
      // initialize MD5 js encryption
      getMd5Crypt()
    }

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
      """(房间已被关闭)""",
      """(该房间目前没有开放)"""
    )

    private const val HS_CDN = "hs-h5"
    private const val TCT_CDN = "tct-h5"
  }

  // DOUYU rid
  internal lateinit var rid: String

  // DOUYU user preferred cdn
  internal var selectedCdn: String = TCT_CDN
  private lateinit var htmlText: String

  init {
    platformHeaders[HttpHeaders.Origin] = DOUYU_URL
    platformHeaders[HttpHeaders.Referrer] = DOUYU_URL
  }

  override val regexPattern: Regex = Regex("""^https://www\.douyu\.com.*""")

  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    val result = getResponse(url)

    if (result.isErr) return result.asErr()

    val response = result.get()!!

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
    if (rid == "房间已被关闭") return Ok(false)
    else if (rid == "该房间目前没有开放") return Err(ExtractorError.StreamerNotFound)

    // check if the stream is live
    val liveStatus = LIVE_STATUS_REGEX.toRegex().find(htmlText)?.groupValues?.get(1)?.toInt()
    // check if the stream is a video loop
    val videoLoop = VIDEO_LOOP_REGEX.toRegex().find(htmlText)?.groupValues?.get(1)?.toInt()
    val isLive = liveStatus == 1 && videoLoop == 0

    return Ok(isLive)
  }

  override suspend fun extract(): Result<MediaInfo, ExtractorError> {
    val isLive = isLive()

    if (isLive.isErr) return isLive.asErr()

    var title = Regex(TITLE_REGEX).find(htmlText)?.groupValues?.get(1) ?: ""
    var artist = Regex(ARTIST_REGEX).find(htmlText)?.groupValues?.get(1) ?: ""
    var avatar = ""
    var cover = ""


    val mediaInfo = MediaInfo(DOUYU_URL, title, artist, cover, avatar, live = isLive.get()!!)

    if (!isLive.get()!!) return Ok(mediaInfo)

    val roomApiResult = getResponse("https://open.douyucdn.cn/api/RoomApi/room/$rid") {
      contentType(ContentType.Application.Json)
    }

    if (roomApiResult.isErr) return roomApiResult.asErr()

    val roomApiResponse = roomApiResult.get()!!

    val jsonText = roomApiResponse.body<JsonElement>()
    logger.debug("{}", jsonText)
    val errorCode = jsonText.jsonObject["error"]?.jsonPrimitive?.intOrNull ?: -1
    val data = if (errorCode == 0) {
      jsonText.jsonObject["data"]?.jsonObject
    } else {
      null
    } ?: return Err(ExtractorError.InvalidResponse("Failed to get room data"))


    title = data["room_name"]?.jsonPrimitive?.content ?: title
    artist = data["owner_name"]?.jsonPrimitive?.content ?: artist
    avatar = data["avatar"]?.jsonPrimitive?.content ?: ""
    cover = data["room_thumb"]?.jsonPrimitive?.content ?: ""


    val jsEncResult = http.getDouyuH5Enc(json, htmlText, rid)

    if (jsEncResult.isErr) return jsEncResult.asErr()

    val jsEnc = jsEncResult.get()!!
    logger.trace("jsEnc: $jsEnc")
    val paramsResult = withContext(Dispatchers.Default) { ub98484234(jsEnc, rid) }

    if (paramsResult.isErr) return paramsResult.asErr()

    val streams = mutableListOf<StreamInfo>()

    val paramsMap = paramsResult.get()!!

    val streamInfoResult = getStreamInfo(selectedCdn = selectedCdn, encMap = paramsMap)
    if (streamInfoResult.isErr) {
      return streamInfoResult.asErr()
    }

    val (streamInfo, rates) = streamInfoResult.get()!!
    streams.add(streamInfo)

    // get the rest of the stream info
    // exclude the first one
    for (i in 1 until rates.size) {
      val rateJson = rates[i].jsonObject
      val rateInfoResult = getRateInfo(rateJson)

      val rateInfo = rateInfoResult.get()!!

      if (rateInfoResult.isErr) {
        logger.error("cdn: $selectedCdn failed to get rate info of rate: ${rates[i]}: {}", rateInfoResult.getError())
        continue
      }
      val streamInfoRateResult =
        getStreamInfo(selectedCdn = selectedCdn, selectedRate = rateInfo["rate"]!!, encMap = paramsMap)
      if (streamInfoRateResult.isErr) {
        logger.error(
          "cdn: $selectedCdn failed to get stream info for rate: ${rateInfo["rate"]}: {}",
          streamInfoRateResult.getError()
        )
        continue
      }
      val stream = streamInfoRateResult.get()!!.first
      streams.add(stream)
    }
    logger.trace("$url streams: {}", pprint(streams))
    return Ok(
      mediaInfo.copy(
        title = title,
        artist = artist,
        artistImageUrl = avatar,
        coverUrl = cover,
        streams = streams
      )
    )
  }

  private suspend fun getStreamInfo(
    selectedCdn: String = "",
    selectedRate: String = "0",
    encMap: Map<String, Any?>,
  ): Result<Pair<StreamInfo, JsonArray>, ExtractorError> {
    val result = postResponse("https://www.douyu.com/lapi/live/getH5Play/$rid") {
      //  ws-5（线路1） tctc-h5（备用线路4）, tct-h5（备用线路5）, ali-h5（备用线路6）, hw-h5（备用线路7）, hs-h5（备用线路13）
      val cdn = if (selectedCdn == HS_CDN) {
        TCT_CDN
      } else {
        selectedCdn
      }
      parameter("cdn", cdn)
      parameter("rate", selectedRate)
      parameter("iar", "0")
      parameter("ive", "0")
      encMap.forEach { (key, value) ->
        if (value != null)
          parameter(key, value.toString())
      }
      contentType(ContentType.Application.FormUrlEncoded)
    }

    logger.debug("result {}", result)

    if (result.isErr) {
      return result.asErr()
    }

    val liveDataResponse = result.get()!!

    val liveDataJsonResult = runCatching { json.parseToJsonElement(liveDataResponse.bodyAsText()) }
      .mapError { ExtractorError.InvalidResponse("live data parse failed for cdn: $selectedCdn, rate: $selectedRate") }

    if (liveDataJsonResult.isErr) return liveDataJsonResult.asErr()

    val liveDataJson = liveDataJsonResult.get()!!
    logger.debug("{}", liveDataJson)
    val error = liveDataJson.jsonObject["error"]?.jsonPrimitive?.intOrNull ?: -1

    if (error != 0) {
      val msg = liveDataJson.jsonObject["msg"]?.jsonPrimitive?.content
      return Err(ExtractorError.InvalidResponse("failed to get live data, cdn:$selectedCdn, rate: $selectedRate returned error code $error, msg: $msg"))
    }

    // current live data, should be the first one, with highest bitrate and quality
    val liveData = liveDataJson.jsonObject["data"]?.jsonObject!!
    val rtmpUrl = liveData["rtmp_url"]!!.jsonPrimitive.content
    val rtmpLive = liveData["rtmp_live"]!!.jsonPrimitive.content
    // url = rtmp_url + rtmp_live
    val url = "$rtmpUrl/$rtmpLive".run {
      // HACK, source: <a href="https://biliup.me/d/158-douyu-%E6%9E%84%E9%80%A0%E7%81%AB%E5%B1%B1cdn%E6%B5%81%E9%93%BE%E6%8E%A5">构造火山cdn流链接</a>
      if (selectedCdn == HS_CDN) {
        // url encoded
        val encodedUrl = encodeURLPath()
        val host = Url(rtmpUrl).host
        "https://douyu-pull.s.volcfcdndvs.com/live/${rtmpLive}&fp_user_url=$encodedUrl+&vhost=${host}&domain=${host}"
      } else this
    }
    // list of supported rates
    val multiRates = liveData["multirates"]?.jsonArray
    // rate should be 0, but we obtain it from the live data just in case
    val rate = liveData["rate"]?.jsonPrimitive?.intOrNull ?: 0
    // cdn, usually tct-h5
    val cdn = if (selectedCdn == HS_CDN)
      HS_CDN
    else
      liveData["rtmp_cdn"]!!.jsonPrimitive.content

    val qualityRateInfo =
      getRateInfo(multiRates!!.find { it.jsonObject["rate"]!!.jsonPrimitive.int == rate }!!.jsonObject)
    if (qualityRateInfo.isErr) return qualityRateInfo.asErr()

    val qualityName = qualityRateInfo.get()!!

    return Ok(
      StreamInfo(
        url = url,
        format = VideoFormat.flv,
        quality = qualityName["name"]!!,
        bitrate = qualityName["bitrate"]!!.toLong(),
        priority = qualityName["highBit"]!!.toInt(),
        frameRate = 0.0,
        extras = mapOf("cdn" to cdn, "rate" to qualityName["rate"]!!)
      ) to multiRates
    )
  }

  private fun getRateInfo(info: JsonObject) = runCatching {
    val rate = info["rate"]!!.jsonPrimitive.int
    val bit = info["bit"]!!.jsonPrimitive.int
    val name = info["name"]!!.jsonPrimitive.content
    val highBit = info["highBit"]!!.jsonPrimitive.content

    mapOf(
      "name" to name,
      "highBit" to highBit,
      "rate" to rate.toString(),
      "bitrate" to bit.toString()
    )
  }.mapError {
    ExtractorError.InvalidResponse("failed to parsed rate info")
  }

}