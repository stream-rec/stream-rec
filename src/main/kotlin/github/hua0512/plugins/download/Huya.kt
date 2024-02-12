package github.hua0512.plugins.download

import github.hua0512.app.App
import github.hua0512.data.Streamer
import github.hua0512.data.config.HuyaDownloadConfig
import github.hua0512.plugins.base.Danmu
import github.hua0512.plugins.base.Download
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8
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
    logger.debug("Check should download in coroutine scope : {}, parent : {}", coroutineContext, coroutineContext[Job.Key])
    this.streamer = streamer
    val url = streamer.url
    val roomId = try {
      val matchResult = regexPattern.toRegex().find(url) ?: return false
      matchResult.groupValues.last()
    } catch (e: Exception) {
      logger.error("Streamer : ${streamer.name} url is not valid")
      return false
    }

    if (roomId.isEmpty()) {
      logger.error("Streamer : ${streamer.name} url is not valid")
      return false
    }

    val response: HttpResponse = withContext(Dispatchers.IO) {
      app.client.get("$BASE_URL/$roomId") {
        headers {
          commonHeaders.forEach { append(it.first, it.second) }
          platformHeaders.forEach { append(it.first, it.second) }
        }
      }
    }

    if (response.status != HttpStatusCode.OK) {
      logger.debug("Streamer : {} response status is not OK : {}", streamer.name, response.status)
      return false
    }
    val body = response.bodyAsText()

    if (body.contains("找不到这个主播")) {
      logger.error("Streamer : ${streamer.name} url is not valid")
      return false
    }

    val pattern = "var TT_ROOM_DATA = (.*?);".toRegex()
    val matchResult = pattern.find(body)
    val match = matchResult?.value ?: ""
    if (match.isEmpty()) {
      logger.error("${streamer.name} match is empty")
      return false
    }
    val matchJson = matchResult?.groupValues?.get(1) ?: ""

    if (matchJson.isEmpty()) {
      logger.error("${streamer.name} matchJson is empty")
      return false
    }
    val state = app.json.parseToJsonElement(matchJson).jsonObject["state"]?.jsonPrimitive?.content ?: ""
    if (state != "ON") {
      logger.debug("Streamer : ${streamer.name} is not live")
      return false
    }

    return withContext(Dispatchers.Default) {
      try {
        val html = body.split("stream: ")[1].split("};")[0]
        val json = app.json.parseToJsonElement(html)

        val data = json.jsonObject
        val vMultiStreamInfo = data["vMultiStreamInfo"] ?: run {
          logger.debug("Streamer : ${streamer.name} is not live")
          return@withContext false
        }
        val gameLiveInfo = data["data"]?.jsonArray?.get(0)?.jsonObject?.get("gameLiveInfo")?.jsonObject ?: run {
          logger.debug("Streamer : ${streamer.name} is not live")
          return@withContext false
        }

        val gameStreamInfoList = data["data"]?.jsonArray?.get(0)?.jsonObject?.get("gameStreamInfoList")?.jsonArray.run {
          if (this.isNullOrEmpty()) null
          else this
        } ?: run {
          logger.debug("Streamer : ${streamer.name} is not live")
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
              logger.error("Preselected CDN ($preselectedCdn) not found for ${streamer.name}")
              gameStreamInfoList.firstOrNull()?.jsonObject
            }
        } ?: run {
          logger.error("Error parsing streamInfo for ${streamer.name}")
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
        val seqId = uid + (System.currentTimeMillis() / 1000).toInt()
        val wsSecretPrefix = query["fm"]?.decodeBase64String()?.split("_")?.get(0) ?: ""
        val wsSecretHash = "$seqId|${query["ctype"]}|${platformId}".encodeUtf8().md5().hex()
        val wsSecret = "${wsSecretPrefix}_${convertUid}_${sStreamName}_${wsSecretHash}_${wsTime}".encodeUtf8().md5().hex()
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
          "$sFlvUrl/$sStreamName.$sFlvUrlSuffix?wsSecret=$wsSecret&wsTime=$wsTime&seqid=$seqId&ctype=${query["ctype"]}&ver=1&fs=${query["fs"]}&u=$convertUid&t=$platformId&sv=2401090219&sdk_sid=${(System.currentTimeMillis() / 1000).toInt()}&codec=264"
        if (maxSupportedBitrate != maxBitRate) {
          downloadUrl = "$downloadUrl&ratio=$maxSupportedBitrate"
        }

        // The live status update on Huya experiences considerable delay (>1minute)
        // so we need to check if the streamer is still live
        if (streamer.isLive)
        // this request should timeout if the streamer is not live
          return@withContext withContext(Dispatchers.IO) {
            app.client.prepareGet(downloadUrl) {
              headers {
                append(HttpHeaders.Accept, "*/*")
                append(HttpHeaders.AcceptEncoding, "gzip, deflate, br")
                platformHeaders.forEach { append(it.first, it.second) }
              }
            }.execute {
              val channel: ByteReadChannel = it.body()
              channel.availableForRead > 0
            }
          }
        else true
      } catch (e: HttpRequestTimeoutException) {
        false
      } catch (e: Exception) {
        logger.error("(${streamer.name}) checking is live with error : ${e.message}")
        false
      }
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