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

package github.hua0512.plugins.twitch.download

import com.github.michaelbull.result.*
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.platform.HlsQuality
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.utils.generateRandomString
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import kotlinx.serialization.json.*
import kotlin.collections.set

/**
 * Extractor for Twitch. Used to extract media information from Twitch.
 * @param http HttpClient
 * @param json Json
 * @author hua0512
 * @date : 2024/4/27 14:29
 */
class TwitchExtractor(http: HttpClient, json: Json, override val url: String) : Extractor(http, json) {

  companion object {
    internal const val BASE_URL = "https://www.twitch.tv"
    internal const val URL_REGEX = """https?://(?:www\.)?twitch\.tv/([^/]+)"""
  }

  override val regexPattern: Regex = URL_REGEX.toRegex()

  internal var id: String = ""
  var authToken: String = ""

  private lateinit var bodyJson: JsonObject


  init {
    platformHeaders[CLIENT_ID_HEADER] = CLIENT_ID
    platformHeaders[HttpHeaders.Referrer] = BASE_URL
    platformHeaders[DEVICE_ID_HEADER] = generateRandomString(16, noUpperLetters = true)
  }

  override fun match(): Result<String, ExtractorError> = super.match().andThen {
    id = regexPattern.find(url)?.groupValues?.get(1) ?: ""
    if (id.isNotEmpty()) {
      Ok(id)
    } else {
      Err(ExtractorError.InvalidExtractionUrl)
    }
  }


  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    // queries
    val queries = arrayOf(
      buildPersistedQueryRequest(
        "ChannelShell",
        "fea4573a7bf2644f5b3f2cbbdcbee0d17312e48d2e55f080589d053aad353f11",
        buildJsonObject {
          put("login", id)
        }
      ),
      buildPersistedQueryRequest(
        "StreamMetadata",
        "b57f9b910f8cd1a4659d894fe7550ccc81ec9052c01e438b290fd66a040b9b93",
        buildJsonObject {
          put("channelLogin", id)
          put("includeIsDJ", true)
        }
      ),
    )
    if (authToken.isNotEmpty()) {
      platformHeaders[HttpHeaders.Authorization] = "${AuthScheme.OAuth} $authToken"
    }
    val apiResult = twitchPostQPL(http, json, queries.contentToString(), getRequestHeaders())

    if (apiResult.isErr) {
      return apiResult.asErr()
    }
    val response = apiResult.value
    val data =
      response.jsonArray[1].jsonObject["data"]?.jsonObject
        ?: return Err(ExtractorError.InvalidResponse("($id) failed to get stream metadata, response: $response"))
    val user = data["user"] as? JsonObject
      ?: return Err(ExtractorError.InvalidResponse("($id) user not found, response: $response"))

    val stream = user["stream"] ?: return Ok(false)
    // if stream is not live, stream is a JsonNull
    if (stream !is JsonObject) return Ok(false)
    // get stream type
    val type = stream["type"] ?: return Ok(false)
    if (type.jsonPrimitive.content != "live") {
      return Ok(false)
    }
    bodyJson = user
    return Ok(true)
  }


  override suspend fun extract(): Result<MediaInfo, ExtractorError> {
    val liveResult = isLive()
    if (liveResult.isErr) {
      return liveResult.asErr()
    }
    var mediaInfo = MediaInfo(url, "", id, "", "")

    val live = liveResult.value
    if (!live) {
      return Ok(mediaInfo)
    }

    val lastBroadcast =
      bodyJson["lastBroadcast"]?.jsonObject ?: throw IllegalArgumentException("Invalid response, response: $bodyJson")
    val title = lastBroadcast["title"]?.jsonPrimitive?.content ?: ""
    val artistProfileUrl = bodyJson["profileImageURL"]?.jsonPrimitive?.content ?: ""

    // skip stream info extraction, only return basic info
    if (skipStreamInfo) {
      val streamInfo = StreamInfo(url, VideoFormat.hls, HlsQuality.Source.value, 0, 0)
      mediaInfo =
        mediaInfo.copy(artistImageUrl = artistProfileUrl, title = title, live = true, streams = listOf(streamInfo))
      return Ok(mediaInfo)
    }

    val accessTokenApiResult = twitchPostQPL(
      http,
      json,
      buildPersistedQueryRequest(
        "PlaybackAccessToken",
        "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9",
        buildJsonObject {
          put("isLive", true)
          put("login", id)
          put("isVod", false)
          put("vodID", "")
          put("playerType", "site")
          put("isClip", false)
          put("clipID", "")
          put("platform", "site")
        }),
      getRequestHeaders()
    )

    if (accessTokenApiResult.isErr) {
      return accessTokenApiResult.asErr()
    }
    val accessTokenResponse = accessTokenApiResult.value

    val accessToken = accessTokenResponse.jsonObject["data"]?.jsonObject?.get("streamPlaybackAccessToken")?.jsonObject
      ?: return Err(ExtractorError.InvalidResponse("($id) failed to get stream playback access token, response: $accessTokenResponse"))

    val valueToken = accessToken["value"]?.jsonPrimitive?.content ?: ""
    val signature = accessToken["signature"]?.jsonPrimitive?.content ?: ""

    val resp = getResponse("https://usher.ttvnw.net/api/channel/hls/$id.m3u8") {
      parameter("player", "twitchweb")
      parameter("p", (System.currentTimeMillis() / 1000).toString())
      parameter("allow_source", "true")
      parameter("allow_audio_only", "true")
      parameter("allow_spectre", "true")
      parameter("fast_bread", "true")
      parameter("sig", signature)
      parameter("token", valueToken)
    }
    if (resp.isErr) return resp.asErr()

    val hlsString = resp.value.bodyAsText()
    val parseResult = parseHlsPlaylist(hlsString)
    if (parseResult.isErr) {
      return parseResult.asErr()
    }
    val streams = parseResult.value
    mediaInfo = mediaInfo.copy(artistImageUrl = artistProfileUrl, title = title, live = true, streams = streams)
    return Ok(mediaInfo)
  }
}