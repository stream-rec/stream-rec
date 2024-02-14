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

lateinit var douyinTTwid: String

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

suspend fun HttpClient.getDouyinTTwid(): String {
  if (::douyinTTwid.isInitialized) {
    return douyinTTwid
  }
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