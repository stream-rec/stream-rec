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

import com.github.michaelbull.result.*
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.APP_ROOM_REFLOW
import io.exoquery.pprint
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Douyin live stream extractor
 * @author hua0512
 * @date : 2024/10/24 12:10
 */
class DouyinCombinedApiExtractor(http: HttpClient, json: Json, override val url: String) :
  DouyinExtractor(http, json, url) {

  private var hasPcApiFailed = false

  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    hasPcApiFailed = false

    // try first to fetch using pc live api
    var isLive = super.isLive()

    // return error if not a fallback error
    if (isLive.isErr && isLive.error !is ExtractorError.FallbackError) {
      return isLive
    } else if (isLive.isOk) {
      return isLive
    }

    val debugInfo = buildJsonObject {
      put("url", url)
      put("result", isLive.toString())
      put("webRid", webRid)
      put("idStr", idStr)
      put("cookies", cookies)
    }

    logger.debug("{} pc api failed, falling back to mobile api: {}", url, pprint(debugInfo))
    // retry using mobile api
    hasPcApiFailed = true
    val result = getResponse(APP_ROOM_REFLOW) {
      fillDouyinAppCommonParams()
      fillSecUid(secRid)
      // no id str checking yet
      parameter(DouyinParams.ROOM_ID_KEY, idStr.ifEmpty { "2" })
      // find msToken from cookies
      val msToken = parseCookies(cookies)["msToken"]
      if (msToken != null) {
        parameter("msToken", msToken)
      }
      contentType(ContentType.Application.Json)
    }


    if (result.isErr) return result.asErr()

    val response = result.value

    return result.andThen {
      liveData = response.body<JsonElement>()
      val dataObj = liveData.jsonObject["data"]?.jsonObject
        ?: return@andThen Err(ExtractorError.InvalidResponse("No data object found in response"))

      val roomInfo = dataObj["room"]?.jsonObject
        ?: return@andThen Err(ExtractorError.InvalidResponse("No room object found in response"))


      roomInfo["owner"]?.jsonObject?.get("web_rid")?.let { field ->
        webRid = field.jsonPrimitive.content
      }

      liveData = roomInfo
      val status = roomInfo["status"]?.jsonPrimitive?.int
        ?: return@andThen Err(ExtractorError.InvalidResponse("No status found in response"))

      isLive = Ok(status == 2)
      isLive
    }
  }
}