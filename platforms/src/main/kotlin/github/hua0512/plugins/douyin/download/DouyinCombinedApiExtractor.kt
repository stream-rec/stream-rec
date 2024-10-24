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

import github.hua0512.plugins.base.exceptions.InvalidExtractionResponseException
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.APP_ROOM_REFLOW
import github.hua0512.utils.mainLogger
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

  override suspend fun isLive(): Boolean {
    var isLive = false
    hasPcApiFailed = false
    try {
      // try first to fetch using pc live api
      isLive = super.isLive()
    } catch (_: FallbackToDouyinMobileException) {
      hasPcApiFailed = true
      val mobileResponse = getResponse(APP_ROOM_REFLOW) {
        fillDouyinAppCommonParams()
        fillSecUid(secRid)
        // no id str check preset
        parameter(DouyinParams.ROOM_ID_KEY, idStr.ifEmpty { "2" })
      }
      if (!(mobileResponse.status.isSuccess())) {
        throw InvalidExtractionResponseException("$url mobile api failed, status code = ${mobileResponse.status}")
      }
      liveData = mobileResponse.body<JsonElement>()

      val dataObj = liveData.jsonObject["data"]?.jsonObject
        ?: throw InvalidExtractionResponseException("$url mobile api failed to get data")

      mainLogger.debug("$url dataObj : $dataObj")
      val roomInfo = dataObj["room"]?.jsonObject
        ?: throw InvalidExtractionResponseException("$url mobile api failed to get room info")

      roomInfo["owner"]?.jsonObject?.get("web_rid")?.let { field ->
        webRid = field.jsonPrimitive.content
      }

      liveData = roomInfo

      val status = roomInfo["status"]?.jsonPrimitive?.int
        ?: throw InvalidExtractionResponseException("$url mobile api failed to get status")
      if (status != 2) {
        return false
      }
      isLive = true
    } catch (e: Exception) {
      // throw in rest of cases
      throw e
    }
    return isLive
  }
}