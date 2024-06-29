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

package github.hua0512.plugins.base

import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.download.COMMON_HEADERS
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

    private val bandwidthPattern = Regex("BANDWIDTH=(\\d+)")
    private val resolutionPattern = Regex("RESOLUTION=(\\d+x\\d+)")
    private val videoPattern = Regex("VIDEO=\"([^\"]+)\"")
    private val frameRatePattern = Regex("FRAME-RATE=(\\d+)")

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
   */
  open suspend fun prepare() {
    if (!match()) {
      throw IllegalArgumentException("The url $url does not match the pattern")
    }
  }

  /**
   * Function to match the url with the regex pattern
   * @return a boolean value
   */
  protected open fun match(): Boolean = regexPattern.matches(url)

  /**
   * Function to check if the stream is live
   * @return a boolean value
   */
  protected abstract suspend fun isLive(): Boolean

  /**
   * Function to extract the media info from the stream
   * @return a [MediaInfo] object
   * @see MediaInfo
   */
  abstract suspend fun extract(): MediaInfo

  /**
   * get the response from the input url
   *
   * request uses predefined headers and params:
   * [COMMON_HEADERS], [platformHeaders], [platformParams]
   *
   * @param url the input url
   * @param request the request builder
   * @return a [HttpResponse] object
   */
  suspend fun getResponse(url: String, request: HttpRequestBuilder.() -> Unit = {}): HttpResponse = http.get(url) {
    populateCommons()
    request()
  }


  /**
   * post the response from the input url
   * request uses predefined headers and params:
   * [COMMON_HEADERS], [platformHeaders], [platformParams]
   * @param url the input url
   * @param request the request builder
   * @return a [HttpResponse] object
   */
  suspend fun postResponse(url: String, request: HttpRequestBuilder.() -> Unit = {}): HttpResponse = http.post(url) {
    populateCommons()
    request()
  }


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

  /**
   * Parses a hls playlist response and returns a list of [StreamInfo]
   * @param response the input response
   * @return a list of [StreamInfo]
   */
  protected suspend fun parseHlsPlaylist(response: HttpResponse): List<StreamInfo> {
    val body = response.bodyAsText()
    val streams = mutableListOf<StreamInfo>()
    val lines = body.lines()
    lines.indices.filter { lines[it].startsWith("#EXT-X-STREAM-INF") }.forEach { index ->
      val line = lines[index]
      val bandwidth = bandwidthPattern.find(line)?.groupValues?.get(1)?.toLong() ?: 0
      val resolution = resolutionPattern.find(line)?.groupValues?.get(1)?.let { mapOf("resolution" to it) } ?: emptyMap()
      val video = videoPattern.find(line)?.groupValues?.get(1)!!
      val frameRate = frameRatePattern.find(line)?.groupValues?.get(1)?.toDouble() ?: 0.0
      val url = lines[index + 1]
      streams.add(StreamInfo(url, VideoFormat.hls, video, bandwidth, frameRate = frameRate, extras = resolution))
    }
    return streams.toList()
  }
}