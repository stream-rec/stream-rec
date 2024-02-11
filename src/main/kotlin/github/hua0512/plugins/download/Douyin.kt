package github.hua0512.plugins.download

import github.hua0512.app.App
import github.hua0512.data.Streamer
import github.hua0512.data.config.DouyinDownloadConfig
import github.hua0512.data.platform.DouyinQuality
import github.hua0512.plugins.base.Danmu
import github.hua0512.plugins.base.Download
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class Douyin(app: App, danmu: Danmu) : Download(app, danmu) {


  companion object {
    private const val BASE_URL = "https://www.douyin.com"

    internal const val REGEX = "(?:https?://)?(?:www\\.)?(?:live\\.)?douyin\\.com/([a-zA-Z0-9]+)"
  }

  override val regexPattern: String = REGEX

  override suspend fun shouldDownload(streamer: Streamer): Boolean {
    this.streamer = streamer

    val roomId = try {
      streamer.url.split("douyin.com/")[1].split('/')[0].split('?')[0].run {
        if (this.isEmpty()) {
          logger.error("Empty room id from url: ${streamer.url}")
          return false
        }
        this
      }
    } catch (e: Exception) {
      logger.error("Failed to get room id from url: ${streamer.url}")
      return false
    }

//    logger.debug("Room id: {}", roomId)
    val config = streamer.downloadConfig as? DouyinDownloadConfig ?: DouyinDownloadConfig()


    val cookies = (config.cookies ?: app.config.douyinConfig.cookies).also {
      if (it.isNullOrEmpty()) {
        logger.error("Please provide douyin cookies!")
        return false
      }
      if ("ttwid" !in it) {
        // TODO : Add a way to get ttwid
        logger.error("Invalid douyin cookies, ttwid not found!")
        return false
      }
    }

    val response = withContext(Dispatchers.IO) {
      app.client.get("https://live.douyin.com/webcast/room/web/enter/") {
        headers {
          commonHeaders.forEach { append(it.first, it.second) }
          append(HttpHeaders.Referrer, "https://live.douyin.com")
          append(HttpHeaders.Cookie, cookies!!)
        }
        parameter("aid", "6383")
        parameter("web_rid", roomId)
      }
    }

    if (response.status != HttpStatusCode.OK) {
      logger.debug("Streamer : {} response status is not OK : {}", streamer.name, response.status)
      return false
    }

    val data = response.bodyAsText()
//    logger.debug("(${streamer.name}) data: $data")
    val json = app.json.parseToJsonElement(data)
    val liveData = json.jsonObject["data"]?.jsonObject?.get("data")?.jsonArray?.get(0)?.jsonObject ?: run {
      logger.debug("${streamer.name} unable to get live data")
      return false
    }

    downloadTitle = liveData["title"]?.jsonPrimitive?.content ?: run {
      logger.debug("${streamer.name} unable to get live title")
      return false
    }

    val status = liveData["status"]?.jsonPrimitive?.int ?: run {
      logger.debug("${streamer.name} unable to get live status")
      return false
    }

    if (status != 2) {
      logger.info("Streamer : ${streamer.name} is not live")
      return false
    }

    val selectedQuality = (config.quality?.value ?: app.config.douyinConfig.quality.value).run {
      this.ifEmpty { DouyinQuality.origin.value }
    }

    val streamDataJson =
      liveData["stream_url"]?.jsonObject?.get("live_core_sdk_data")?.jsonObject?.get("pull_data")?.jsonObject?.get("stream_data")?.jsonPrimitive?.content
        ?: run {
          logger.error("(${streamer.name}) unable to get stream data")
          return false
        }

    val streamsData = app.json.parseToJsonElement(streamDataJson).jsonObject["data"]?.jsonObject ?: run {
      logger.error("(${streamer.name}) unable to parse stream data")
      return false
    }

    val streamData = streamsData[selectedQuality]?.jsonObject ?: run {
      logger.error("(${streamer.name}) unable to get stream data for quality: $selectedQuality")
      return false
    }

    downloadUrl = streamData["main"]?.jsonObject?.get("flv")?.jsonPrimitive?.content ?: run {
      logger.error("(${streamer.name}) unable to get stream url")
      return false
    }

    logger.debug("({}) json: {}", streamer.name, streamData)
    return true
  }


}