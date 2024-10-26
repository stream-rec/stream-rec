package github.hua0512.plugins.tiktok

import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.exceptions.InvalidExtractionParamsException
import github.hua0512.plugins.base.exceptions.InvalidExtractionResponseException
import github.hua0512.plugins.douyin.download.fillWebRid
import github.hua0512.plugins.tiktok.download.TiktokApis
import github.hua0512.plugins.tiktok.download.populateTiktokCookieMissedParams
import github.hua0512.utils.mainLogger
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class TikTokExtractor(http: HttpClient, json: Json, override val url: String) : Extractor(http, json) {

  companion object {
    private const val URL_REGEX_PATTERN = "https://www.tiktok.com/@([a-zA-Z0-9_\\\\.]+)/live"

    private const val LIVE_DATA_REGEX_PATTERN = "<script id=\"SIGI_STATE\" type=\"application/json\">(.*?)</script>"
  }

  override val regexPattern: Regex = URL_REGEX_PATTERN.toRegex()

  protected lateinit var webRid: String
  protected lateinit var secUid: String
  protected lateinit var roomId: String

  protected var liveData: JsonElement = JsonNull

  init {
    platformHeaders[HttpHeaders.Referrer] = TiktokApis.BASE_URL
  }

  override fun match(): Boolean {
    webRid = extractTiktokWebRid(url) ?: return false
    return super.match()
  }

  fun extractTiktokWebRid(url: String): String? {
    val matchResult = regexPattern.find(url) ?: return null
    return matchResult.groupValues[1]
  }


  override suspend fun isLive(): Boolean {
    cookies = populateTiktokCookieMissedParams(cookies, http)

    liveData = JsonNull
    val response = getResponse(url) {
      fillWebRid(webRid)
    }
    if (!(response.status.isSuccess())) throw InvalidExtractionResponseException("$url status code: ${response.status}")
    val body = response.bodyAsText()

    val liveDataMatchResult = LIVE_DATA_REGEX_PATTERN.toRegex().find(body)
      ?: throw InvalidExtractionResponseException("$url failed to find live data")

    val liveDataText = liveDataMatchResult.groupValues[1]

    if ("We regret to inform you that we have discontinued operating" in liveDataText)
      throw InvalidExtractionResponseException("$url support is discontinued by tiktok")

    liveData = json.parseToJsonElement(liveDataMatchResult.groupValues[1])

    val liveRoom = liveData.jsonObject["LiveRoom"]?.jsonObject
      ?: throw InvalidExtractionResponseException("$url failed to get live room")

    val userInfo = liveRoom["liveRoomUserInfo"]?.jsonObject?.get("user")?.jsonObject
      ?: throw InvalidExtractionResponseException("$url failed to get user info")

    secUid = userInfo["secUid"]?.jsonPrimitive?.content ?: ""
    roomId = userInfo["roomId"]?.jsonPrimitive?.content ?: ""
    val status = userInfo["status"]?.jsonPrimitive?.intOrNull ?: 0
    return status == 2
  }

  override suspend fun extract(): MediaInfo {
    val isLive = isLive()
    var mediaInfo = MediaInfo(url, "", "", "", "", isLive)

    if (liveData is JsonNull) {
      return mediaInfo
    }

    // live data is not null
    assert(liveData is JsonObject)

    val liveRoom = liveData.jsonObject["LiveRoom"]!!.jsonObject
    val liveRoomInfo = liveRoom["liveRoomUserInfo"]!!.jsonObject
    val userInfo = liveRoomInfo["user"]!!.jsonObject
    val nickname = userInfo["nickname"]!!.jsonPrimitive.content
    val avatarUrl = userInfo["avatarThumb"]!!.jsonPrimitive.content

    mediaInfo = mediaInfo.copy(url, "", nickname, "", avatarUrl)

    val liveRoomInfoInternal = liveRoomInfo["liveRoom"]?.jsonObject ?: return mediaInfo

    val coverUrl = liveRoomInfoInternal["coverUrl"]!!.jsonPrimitive.content
    val title = liveRoomInfoInternal["title"]!!.jsonPrimitive.content
    mediaInfo = mediaInfo.copy(url, title, nickname, coverUrl, avatarUrl)

    // TODO : SUPPORT HEVC ('hevcStreamData') field
    val streamData = liveRoomInfoInternal["streamData"]?.jsonObject
      ?: throw InvalidExtractionParamsException("$url failed to get stream data")

    val pullData =
      streamData["pull_data"]?.jsonObject ?: throw InvalidExtractionParamsException("$url failed to get pull data")

    val pullDataOptions = pullData["options"]?.jsonObject
      ?: throw InvalidExtractionParamsException("$url failed to get pull data options")

    val qualities =
      pullDataOptions["qualities"]?.jsonArray ?: throw InvalidExtractionParamsException("$url failed to get qualities")


    if (qualities.isEmpty()) {
      throw InvalidExtractionParamsException("$url stream quality list is empty")
    }

    val streamDataText =
      pullData["stream_data"]?.jsonPrimitive?.contentOrNull?.ifBlank {
        throw InvalidExtractionParamsException("$url failed to get stream data text")
      } ?: throw InvalidExtractionParamsException("$url failed to get stream data text")

    val streamDataJson = json.parseToJsonElement(streamDataText)

    // check if data section is present
    if (streamDataJson !is JsonObject || streamDataJson["data"] == null) {
      throw InvalidExtractionParamsException("$url failed to get stream data json")
    }
    val streams = mutableListOf<StreamInfo>()
    val data = streamDataJson["data"]!!.jsonObject

    // manually add audio stream
    val finalQualities = qualities + buildJsonObject {
      put("name", "audio")
      put("sdk_key", "ao")
    }

    finalQualities.forEach {
      val quality = it.jsonObject
      val qualityName = quality["name"]?.jsonPrimitive?.content ?: ""
      val sdkKey = quality["sdk_key"]?.jsonPrimitive?.content ?: ""

      val dataStreamInfo = data[sdkKey]?.jsonObject
        ?: run {
          mainLogger.debug("$url unable to get stream data for quality: $qualityName")
          return@forEach
        }

      val flvUrl = dataStreamInfo["main"]?.jsonObject["flv"]?.jsonPrimitive?.contentOrNull ?: ""
      val hlsUrl = dataStreamInfo["main"]?.jsonObject["hls"]?.jsonPrimitive?.contentOrNull ?: ""
      val sdkParams =
        json.parseToJsonElement(
          dataStreamInfo["main"]?.jsonObject["sdk_params"]?.jsonPrimitive?.contentOrNull ?: "{}"
        ).jsonObject
      val codec = sdkParams["VCodec"]?.jsonPrimitive?.contentOrNull ?: ""
      val resolution = sdkParams["resolution"]?.jsonPrimitive?.contentOrNull ?: ""

      if (flvUrl.isNotEmpty()) {
        streams.add(
          StreamInfo(
            url = flvUrl,
            format = VideoFormat.flv,
            quality = qualityName,
            bitrate = sdkParams["vbitrate"]?.jsonPrimitive?.long ?: 0,
            frameRate = 0.0,
            extras = mapOf("sdkKey" to sdkKey)
          )
        )
      }
      if (hlsUrl.isNotEmpty()) {
        streams.add(
          StreamInfo(
            url = hlsUrl,
            format = VideoFormat.hls,
            quality = qualityName,
            bitrate = sdkParams["vbitrate"]?.jsonPrimitive?.long ?: 0,
            frameRate = 0.0,
            extras = mapOf("sdkKey" to sdkKey)
          )
        )
      }
    }
    mediaInfo = mediaInfo.copy(streams = streams)

    return mediaInfo
  }


}