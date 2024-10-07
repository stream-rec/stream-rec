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

package github.hua0512.plugins.douyin.download

import github.hua0512.plugins.base.exceptions.InvalidExtractionUrlException
import github.hua0512.plugins.douyin.download.DouyinExtractor.Companion.URL_REGEX
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter

/** Utils for Douyin requests
 * @author hua0512
 * @date : 2024/10/6 16:40
 */


/**
 * Extracts the Douyin webrid from the specified URL.
 *
 * @param url The URL from which to extract the Douyin webrid
 * @return The Douyin room ID, or `null` if the webrid could not be extracted
 */
internal fun extractDouyinWebRid(url: String): String? {
  if (url.isEmpty()) return null
  val roomIdPattern = URL_REGEX.toRegex()
  return roomIdPattern.find(url)?.groupValues?.get(1) ?: run {
    throw InvalidExtractionUrlException("Failed to get douyin room id from url: $url")
    return null
  }
}

internal fun HttpRequestBuilder.fillDouyinCommonParams() {
  DouyinParams.commonParams.forEach { (t, u) ->
    parameter(t, u)
  }
}

internal fun MutableMap<String, String>.fillDouyinCommonParams() {
  putAll(DouyinParams.commonParams)
}

internal fun HttpRequestBuilder.fillWebRid(webRid: String) {
  parameter(DouyinParams.WEB_RID_KEY, webRid)
}