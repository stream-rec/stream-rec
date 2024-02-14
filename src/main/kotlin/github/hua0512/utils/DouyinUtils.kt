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

package github.hua0512.utils

import github.hua0512.logger
import github.hua0512.plugins.base.Download
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.util.*


/**
 * A map of common parameters used for making requests to the Douyin API.
 *
 * The map contains the following key-value pairs:
 * - "aid" - The Douyin application ID
 * - "device_platform" - The platform of the device making the request (e.g., "web")
 * - "browser_language" - The language of the browser making the request (e.g., "zh-CN")
 * - "browser_platform" - The platform of the browser making the request (e.g., "Win32")
 * - "browser_name" - The name of the browser making the request (e.g., "Chrome")
 * - "browser_version" - The version of the browser making the request (e.g., "98.0.4758.102")
 * - "compress" - The compression method used for the response (e.g., "gzip")
 * - "signature" - The signature for the request
 * - "heartbeatDuration" - The duration of the heartbeat in milliseconds
 */
val commonDouyinParams = mapOf(
  "aid" to "6383",
  "device_platform" to "web",
  "browser_language" to "zh-CN",
  "browser_platform" to "Win32",
  "browser_name" to "Chrome",
  "browser_version" to "98.0.4758.102",
  "compress" to "gzip",
  "signature" to "00000000",
  "heartbeatDuration" to "0"
)

/**
 * The `ttwid` parameter from the Douyin cookies.
 */
private lateinit var douyinTTwid: String

/**
 * The `msToken` parameter to be used in Douyin requests.
 */
private lateinit var douyinMsToken: String

/**
 * Extracts the Douyin room ID from the specified URL.
 *
 * @param url The URL from which to extract the Douyin room ID
 * @return The Douyin room ID, or `null` if the room ID could not be extracted
 */
internal fun extractDouyinRoomId(url: String): String? {
  if (url.isEmpty()) return null
  return try {
    val roomIdPattern = "douyin.com/([^?]*)".toRegex()
    roomIdPattern.find(url)?.groupValues?.get(1) ?: run {
      logger.error("Failed to get douyin room id from url: $url")
      return null
    }
  } catch (e: Exception) {
    logger.error("Failed to get douyin room id from url: $url", e)
    null
  }
}

/**
 * Populates the missing parameters (ttwid or msToken) in the specified Douyin cookies.
 *
 *
 * @param cookies The Douyin cookies to populate
 * @param client The HTTP client to use for making requests
 * @return The Douyin cookies with the missing parameters populated
 */
suspend fun populateDouyinCookieMissedParams(cookies: String, client: HttpClient): String {
  if (cookies.isEmpty()) {
    throw Exception("Empty cookies")
  }
  var finalCookies = cookies
  if ("ttwid" !in cookies) {
    logger.info("ttwid not found in cookies, trying to get it from server...")
    val ttwid = client.getDouyinTTwid()
    finalCookies += "; ttwid=$ttwid"
    logger.info("ttwid not found in cookies, got it from server: $ttwid")
  }
  if ("msToken" !in cookies) {
    val msToken = generateDouyinMsToken()
    finalCookies += "; msToken=$msToken"
    logger.info("msToken not found in cookies, generated a new one: $msToken")
  }
  return finalCookies
}

/**
 * Generates a random string to be used as the `msToken` parameter in Douyin requests.
 *
 * @param length The length of the random string to generate
 * @return A random string to be used as the `msToken` parameter in Douyin requests
 */
private fun generateDouyinMsToken(length: Int = 107): String {
  // return the token if it has already been generated
  if (::douyinMsToken.isInitialized) return douyinMsToken

  // generate a random string, with length 107
  val source = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789=_"
  val random = Random()
  val sb = StringBuilder()
  for (i in 0 until length) {
    sb.append(source[random.nextInt(source.length)])
  }
  return sb.toString().also { douyinMsToken = it }
}


/**
 * Makes a request to the Douyin API to get the `ttwid` parameter from the cookies.
 *
 * @param client The HTTP client to use for making requests
 * @return The `ttwid` parameter from the Douyin cookies
 */
private suspend fun HttpClient.getDouyinTTwid(): String {
  if (::douyinTTwid.isInitialized) return douyinTTwid

  val response = this.get("https://live.douyin.com/1-2-3-4-5-6-7-8-9-0") {
    commonDouyinParams.forEach { (key, value) ->
      parameter(key, value)
    }
    Download.commonHeaders.forEach { (key, value) ->
      header(key, value)
    }
    header(HttpHeaders.Referrer, "https://live.douyin.com")
  }

  val cookies = response.headers[HttpHeaders.SetCookie] ?: ""
  val ttwidPattern = "ttwid=([^;]*)".toRegex()
  val ttwid = ttwidPattern.find(cookies)?.groupValues?.get(1) ?: ""
  if (ttwid.isEmpty()) {
    throw Exception("Failed to get ttwid from cookies")
  }
  douyinTTwid = ttwid
  return ttwid
}