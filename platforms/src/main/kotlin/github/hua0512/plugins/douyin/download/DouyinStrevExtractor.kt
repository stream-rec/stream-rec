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

package github.hua0512.plugins.douyin.download

import com.github.michaelbull.result.Result
import github.hua0512.plugins.StrevExtractor
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.LIVE_DOUYIN_URL
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Douyin live stream extractor using strev api
 * @author hua0512
 * @date : 9/10/2025 12:05 PM
 */
class DouyinStrevExtractor(http: HttpClient, json: Json, override val url: String) : StrevExtractor(http, json, url) {

  internal var idStr = ""

  init {
    platformHeaders[HttpHeaders.Referrer] = LIVE_DOUYIN_URL
  }

  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    val result = super.isLive()
    if (result.isOk) {
      // extract id_str from extras
      idStr = cachedMediaInfo?.streams?.firstOrNull()?.extras?.get("id_str") ?: ""
    }
    return result
  }
}