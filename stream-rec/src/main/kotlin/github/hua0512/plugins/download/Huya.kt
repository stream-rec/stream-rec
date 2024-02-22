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

package github.hua0512.plugins.download

import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig.HuyaDownloadConfig
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.base.Danmu
import github.hua0512.plugins.base.Download
import github.hua0512.utils.toMD5Hex
import github.hua0512.utils.withIOContext
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import java.util.*
import kotlin.coroutines.coroutineContext

class Huya(app: App, danmu: Danmu) : Download(app, danmu) {
  companion object {
    internal const val BASE_URL = "https://www.huya.com"

    internal val platformHeaders = arrayOf(
      HttpHeaders.Origin to BASE_URL,
      HttpHeaders.Referrer to BASE_URL
    )

    internal val REGEX = "(?:https?://)?(?:(?:www|m)\\.)?huya\\.com/([a-zA-Z0-9]+)"
  }

  override val regexPattern: String = REGEX

  override suspend fun shouldDownload(streamer: Streamer): Boolean {
    this.streamer = streamer
    val url = streamer.url
    val roomId = try {
      val matchResult = regexPattern.toRegex().find(url) ?: return false
      matchResult.groupValues.last()
    } catch (e: Exception) {
      throw IllegalArgumentException("(${streamer.name}) url is not valid, ${e.message}")
    }

    if (roomId.isEmpty()) {
      throw IllegalArgumentException("(${streamer.name}) url is not valid")
    }

    val response: HttpResponse = withIOContext {
      app.client.get("$BASE_URL/$roomId") {
        headers {
          commonHeaders.forEach { append(it.first, it.second) }
          platformHeaders.forEach { append(it.first, it.second) }
        }
      }
    }

    if (response.status != HttpStatusCode.OK) {
      logger.debug("({}) response status is not OK : {}", streamer.name, response.status)
      return false
    }
    val body = response.bodyAsText()

    if (body.contains("找不到这个主播")) {
      throw IllegalArgumentException("($${streamer.name}) url is not valid")
    }

    val pattern = "var TT_ROOM_DATA = (.*?);".toRegex()
    val matchResult = pattern.find(body)
    val match = matchResult?.value ?: ""
    if (match.isEmpty()) {
      logger.error("(${streamer.name}) match is empty")
      return false
    }
    val matchJson = matchResult?.groupValues?.get(1) ?: ""

    if (matchJson.isEmpty()) {
      logger.error("(${streamer.name}) matchJson is empty")
      return false
    }
    val state = app.json.parseToJsonElement(matchJson).jsonObject["state"]?.jsonPrimitive?.content ?: ""
    val liveChannel = app.json.parseToJsonElement(matchJson).jsonObject["liveChannel"]?.jsonPrimitive?.longOrNull ?: 0
    if (state != "ON" || liveChannel == 0L) {
      logger.debug("(${streamer.name}) is not live")
      return false
    }

    return withContext(Dispatchers.Default) {

      val html = body.split("stream: ")[1].split("};")[0]
      val json = app.json.parseToJsonElement(html)

      val data = json.jsonObject
      val vMultiStreamInfo = data["vMultiStreamInfo"] ?: run {
        logger.debug("(${streamer.name}) is not live, vMultiStreamInfo is null")
        return@withContext false
      }
      val gameLiveInfo = data["data"]?.jsonArray?.get(0)?.jsonObject?.get("gameLiveInfo")?.jsonObject ?: run {
        logger.debug("(${streamer.name}) is not live, gameLiveInfo is null")
        return@withContext false
      }

      val gameStreamInfoList = data["data"]?.jsonArray?.get(0)?.jsonObject?.get("gameStreamInfoList")?.jsonArray.run {
        if (this.isNullOrEmpty()) null
        else this
      } ?: run {
        logger.debug("(${streamer.name}) is not live, gameStreamInfoList is null")
        return@withContext false
      }

      val userConfig = streamer.downloadConfig as? HuyaDownloadConfig ?: HuyaDownloadConfig()

      // default max bit rate
      val maxBitRate = gameLiveInfo["bitRate"]?.jsonPrimitive?.int ?: 0
      // user defined max bit rate
      val maxUserBitRate = (userConfig.maxBitRate ?: app.config.huyaConfig.maxBitRate) ?: 10000
      // user selected cdn
      var preselectedCdn = userConfig.primaryCdn ?: app.config.huyaConfig.primaryCdn
      preselectedCdn = preselectedCdn.uppercase(Locale.getDefault())

      // get the max bit rate possible
      val maxSupportedBitrate = vMultiStreamInfo.jsonArray
        .mapIndexed { index, jsonElement ->
          var bitRate = jsonElement.jsonObject["iBitRate"]?.jsonPrimitive?.int ?: 0
          // first item is (should be) the highest bit rate
          if (index == 0 && maxBitRate != 0) bitRate = maxBitRate
          bitRate to index
        }
        .filter {
          it.first <= maxUserBitRate
        }.maxByOrNull { it.first }?.first ?: 0

      // stream info
      val streamInfo: JsonObject = if (preselectedCdn.isEmpty()) {
        // choose the highest cdn by iLineIndex (3, 5, 6, 14...)
        gameStreamInfoList.maxBy { it.jsonObject["iLineIndex"]?.jsonPrimitive?.int ?: 0 }.jsonObject
      } else {
        // try to find the preselected cdn
        gameStreamInfoList.firstOrNull { it.jsonObject["sCdnType"]?.jsonPrimitive?.content?.uppercase(Locale.getDefault()) == preselectedCdn }?.jsonObject
          ?: run {
            logger.error("(${streamer.name}) Preselected CDN ($preselectedCdn) not found")
            gameStreamInfoList.firstOrNull()?.jsonObject
          }
      } ?: run {
        logger.error("(${streamer.name}) Error parsing streamInfo")
        return@withContext false
      }

      val sFlvAntiCode = streamInfo["sFlvAntiCode"]?.jsonPrimitive?.content ?: ""
      val sFlvUrl = streamInfo["sFlvUrl"]?.jsonPrimitive?.content ?: ""
      val sFlvUrlSuffix = streamInfo["sFlvUrlSuffix"]?.jsonPrimitive?.content ?: ""
      val sStreamName = streamInfo["sStreamName"]?.jsonPrimitive?.content ?: ""
      val platformId = 100

      val uid: Long = (12340000L..12349999L).random()
      val convertUid = (uid shl 8 or (uid shr (32 - 8))) and -0x1

      val query = parseQuery(sFlvAntiCode)
      val wsTime = query["wsTime"] ?: ""
      val seqId = uid + Clock.System.now().epochSeconds.toInt()
      val wsSecretPrefix = query["fm"]?.decodeBase64String()?.split("_")?.get(0) ?: ""
      val wsSecretHash = "$seqId|${query["ctype"]}|${platformId}".toByteArray().toMD5Hex()
      val wsSecret = "${wsSecretPrefix}_${convertUid}_${sStreamName}_${wsSecretHash}_${wsTime}".toByteArray().toMD5Hex()
      val streamTitle = gameLiveInfo["introduction"]?.jsonPrimitive?.content ?: ""
//            println("gameLiveInfo : $gameLiveInfo")
//            println("gameStreamInfoList : $gameStreamInfoList")
//            println("vMultiStreamInfo : $vMultiStreamInfo")
//            println("sFlvAntiCode : $sFlvAntiCode")
//            println("sFlvUrl : $sFlvUrl")
//            println("sFlvUrlSuffix : $sFlvUrlSuffix")
//            println("platformId : $platformId")
//            println("sStreamName : $sStreamName")
//            println("uid : $uid")
//            println("convertUid : $convertUid")
//            println("query : $query")
//            println("wsTime : $wsTime")
//            println("seqId : $seqId")
//            println("wsSecretPrefix : $wsSecretPrefix")
//            println("wsSecretHash : $wsSecretHash")
//            println("wsSecret : $wsSecret")
//        println("streamTitle : $streamTitle")
//        println("maxBitRate : $maxBitRate")
//        println("maxSupportedBitrate : $maxSupportedBitrate")
      downloadTitle = streamTitle
      downloadUrl =
        "$sFlvUrl/$sStreamName.$sFlvUrlSuffix?wsSecret=$wsSecret&wsTime=$wsTime&seqid=$seqId&ctype=${query["ctype"]}&ver=1&fs=${query["fs"]}&u=$convertUid&t=$platformId&sv=2401090219&sdk_sid=${Clock.System.now().epochSeconds}&codec=264"
      if (maxSupportedBitrate != maxBitRate) {
        downloadUrl = "$downloadUrl&ratio=$maxSupportedBitrate"
      }

      true
    }
  }

  private fun parseQuery(query: String): Map<String, String> {
    val queryMap = mutableMapOf<String, String>()
    query.split("&").forEach {
      val (key, value) = it.split("=")
      queryMap[key] = value
    }
    return queryMap
  }
}