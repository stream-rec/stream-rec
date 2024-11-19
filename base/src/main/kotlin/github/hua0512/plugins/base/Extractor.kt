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

package github.hua0512.plugins.base

import com.github.michaelbull.result.*
import github.hua0512.app.COMMON_HEADERS
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.utils.mapError
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.lindstrom.m3u8.model.MasterPlaylist
import io.lindstrom.m3u8.parser.MasterPlaylistParser
import io.lindstrom.m3u8.parser.MediaPlaylistParser
import io.lindstrom.m3u8.parser.ParsingMode
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

/**
 * The base class for all the extractors.
 *
 * An extractor is a class that is used to extract the media info from an input stream.
 *
 * @property http the http client
 * @property json the json serializer
 * @author hua0512
 * @date : 2024/3/15 19:47
 */
abstract class Extractor(protected open val http: HttpClient, protected open val json: Json) {


  companion object {

    @JvmStatic
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Parses the cookies from the input string
     * @param cookies the input string
     * @return a map of cookies
     */
    @JvmStatic
    protected fun parseCookies(cookies: String): Map<String, String> {
      return parseClientCookiesHeader(cookies)
    }

  }


  /**
   * The cookies to be used to download the stream
   */
  var cookies: String = ""

  /**
   * The platform headers to be used to download the stream
   * @return a map of platform headers
   */
  protected val platformHeaders = mutableMapOf<String, String>()

  /**
   * The platform params to be used to download the stream
   * @return a map of platform params
   */
  protected val platformParams = mutableMapOf<String, String>()

  /**
   * The regex pattern to match the url
   * @return a regex pattern
   * @see Regex
   */
  abstract val regexPattern: Regex

  /**
   * The url of the stream
   * @return a string of the url
   */
  abstract val url: String

  /**
   * Whether to skip the stream info extraction
   */
  var skipStreamInfo = false

  /**
   * Initialize the extractor
   * @return a [Result] object with the value of the url, or an error
   * @see Result
   */
  open fun prepare(): Result<String, ExtractorError> {
    val matchResult = match()

    if (matchResult.isErr) {
      return Err(ExtractorError.InvalidExtractionUrl)
    }
    return Ok(matchResult.value)
  }

  /**
   * Function to match the url with the regex pattern
   * @return a [Result] object with the value of the url, or an error if the url does not match the pattern
   */
  open fun match(): Result<String, ExtractorError> = if (regexPattern.matches(url)) {
    Ok(url)
  } else {
    Err(ExtractorError.InvalidExtractionUrl)
  }


  /**
   * Function to get the platform specific headers
   * @return a map of platform headers
   */
  open fun getRequestHeaders(): Map<String, String> = platformHeaders.toMap()


  /**
   * Function to check if the stream is live
   * @return a boolean value
   */
  protected abstract suspend fun isLive(): Result<Boolean, ExtractorError>

  /**
   * Function to extract the media info from the stream
   * @return a [MediaInfo] object
   * @see MediaInfo
   */
  abstract suspend fun extract(): Result<MediaInfo, ExtractorError>

  /**
   * get the response from the input url
   *
   * request uses predefined headers and params:
   * [COMMON_HEADERS], [platformHeaders], [platformParams]
   *
   * @param url the input url
   * @param request the request builder
   * @return Result of the response
   */
  suspend fun getResponse(url: String, request: HttpRequestBuilder.() -> Unit = {}): Result<HttpResponse, ExtractorError.ApiError> = runCatching {
    http.get(url) {
      populateCommons()
      request()
    }
  }.mapError()

  /**
   * post the response from the input url
   * request uses predefined headers and params:
   * [COMMON_HEADERS], [platformHeaders], [platformParams]
   * @param url the input url
   * @param request the request builder
   * @return Result of the response
   */
  suspend fun postResponse(url: String, request: HttpRequestBuilder.() -> Unit = {}): Result<HttpResponse, ExtractorError.ApiError> = runCatching {
    http.post(url) {
      populateCommons()
      request()
    }
  }.mapError()


  /**
   * populate the common headers and params
   * @receiver the request builder
   * @see HttpRequestBuilder
   */
  private fun HttpRequestBuilder.populateCommons() {
    headers {
      COMMON_HEADERS.forEach { append(it.first, it.second) }
      platformHeaders.forEach { append(it.key, it.value) }
      if (cookies.isNotEmpty()) {
        append(HttpHeaders.Cookie, cookies)
      }
    }
    platformParams.forEach { (t, u) ->
      parameter(t, u)
    }
  }

  private fun isMasterPlaylist(content: String) = content.contains("#EXT-X-STREAM-INF")


  /**
   * Parses a hls playlist response and returns a list of [StreamInfo]
   * @param playlistString the input m3u8 playlist string
   * @return a list of [StreamInfo]
   */
  protected fun parseHlsPlaylist(playlistString: String): Result<List<StreamInfo>, ExtractorError> {
    val streams = mutableListOf<StreamInfo>()
    val parsingMode = ParsingMode.LENIENT
    val mediaParser = if (isMasterPlaylist(playlistString)) {
      MasterPlaylistParser(parsingMode)
    } else {
      MediaPlaylistParser(parsingMode)
    }

    val parseResult = runCatching {
      mediaParser.readPlaylist(playlistString)
    }.mapError {
      ExtractorError.InvalidResponse("Failed to parse playlist")
    }

    if (parseResult.isErr) return parseResult.asErr()

    val playlist = parseResult.unwrap()
    if (playlist is MasterPlaylist) {
      val variants = playlist.variants()
      variants.map {
        val extraMap = it.resolution().getOrNull()?.let { mapOf("resolution" to "${it.height()}x${it.width()}") } ?: emptyMap()

        StreamInfo(
          url,
          format = VideoFormat.hls,
          it.video().getOrElse { "" },
          it.bandwidth(),
          0,
          it.frameRate().getOrElse { 0.0 },
          extras = extraMap
        )
      }
    } else {
      return Err(ExtractorError.InvalidResponse("Media playlist returned instead of master playlist"))
    }

    return Ok(streams.toList())
  }
}