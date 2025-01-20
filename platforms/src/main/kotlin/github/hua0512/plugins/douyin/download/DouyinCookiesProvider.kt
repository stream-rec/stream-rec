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

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.app.COMMON_HEADERS
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.AID_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.AID_VALUE
import github.hua0512.utils.generateRandomString
import github.hua0512.utils.logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Files that contain the cookies used in Douyin requests.
 * @author hua0512
 * @date : 2024/10/6 22:01
 */


// cookie parameters
private var ttWid = atomic<String?>(null)
private var userUniqueId = atomic<Long?>(null)

private const val PROVIDED_TTWID =
  "1%7ChSa8ecMcm1LGdBAkSrZteUZq7JcVmPbdvaMPTmwFqeU%7C1732525885%7Cc224f863bd709bd733ab2384c98993564af45c9d28b71d7a404b82a99f6aaa91"

/**
 * A semaphore to ensure that only one request is made to fetch the cookies at a time.
 */
private val fetchCookiesSemaphore by lazy { Semaphore(1) }

private val logger = logger("DouyinCookiesProvider")


/**
 * Generates a random string to be used as the `__ac_nonce` parameter in Douyin requests.
 * The generated string is 21 characters long.
 * @return A random string to be used as the `__ac_nonce` parameter in Douyin requests
 */

private fun generateNonce(): String {
  return generateRandomString(21, noUpperLetters = true, noNumeric = false, lastChar = 'f')
}

private fun generateMsToken(): String =
  generateRandomString(184, noUpperLetters = false, noNumeric = false, additionalLetters = arrayOf('_', '-'))


/**
 * Populates the missing parameters (ttwid, __ac_nonce) in the specified Douyin cookies.
 *
 * @param cookies The Douyin cookies to populate
 * @param client The HTTP client to use for making requests
 * @return The Douyin cookies with the missing parameters populated
 */
internal suspend fun populateDouyinCookieMissedParams(cookies: String, client: HttpClient): String {
  val parsedCookies = if (cookies.isEmpty()) {
    mutableMapOf()
  } else {
    parseClientCookiesHeader(cookies).toMutableMap()
  }

  val map = parsedCookies.apply {
    getOrPut(TT_WID_COOKIE) {
      val ttwidResult = getDouyinTTwid(client)
      if (ttwidResult.isErr) {
        logger.error("TTwid response error: ${ttwidResult.error}")
        return PROVIDED_TTWID
      } else {
        ttwidResult.value
      }
    }
    getOrPut(ODIN_TT_COOKIE) { generateOdinTT() }
    getOrPut(AC_NONCE_COOKIE) { generateNonce() }
    getOrPut(MS_TOKEN_COOKIE) { generateMsToken() }
  }

  return map.entries.joinToString("; ") { "${it.key}=${it.value}" } + ";"
}

private fun generateOdinTT(): String = generateRandomString(160, noNumeric = false, noUpperLetters = true, 'f')

/**
 * Makes a request to the Douyin API to get the `ttwid` parameter from the cookies.
 *
 * @param client The HTTP client to use for making requests
 * @return The `ttwid` parameter from the Douyin cookies
 */
private suspend fun getDouyinTTwid(client: HttpClient): Result<String, ExtractorError> {
  val currentTtwid = ttWid.value
  if (currentTtwid != null) return Ok(currentTtwid)

  val apiResult = fetchCookiesSemaphore.withPermit {
    ttWid.value?.let { return Ok(it) }

    val response = client.post("https://ttwid.bytedance.com/ttwid/union/register/") {
      COMMON_HEADERS.forEach { (key, value) ->
        // do not add User-Agent header as it is appended automatically
        if (key != HttpHeaders.UserAgent) header(key, value)
      }

      contentType(ContentType.Application.Json)
      setBody(
        buildJsonObject {
          put("region", "cn")
          put(AID_KEY, AID_VALUE.toInt())
          put("needFid", false)
          put("service", DouyinApis.BASE_URL)
          put("union", true)
          put("fid", "")
        }
      )
    }

    val cookiesList = response.setCookie()
    logger.debug("cookies: {}", cookiesList)

    cookiesList.firstOrNull { it.name == TT_WID_COOKIE }?.value?.let { Ok(it) }
      ?: Err(ExtractorError.InvalidResponse("Failed to get ttwid"))
  }

  if (apiResult.isErr) return apiResult

  val successful = ttWid.compareAndSet(null, apiResult.value)
  if (successful) {
    logger.info("$TT_WID_COOKIE(web): ${apiResult.value}")
  }
  // Return the current value of TT_WID, which may be set by another thread
  return Ok(ttWid.value ?: apiResult.value)
}

private fun generateUserId() = Random.nextLong(720_000_000_000_000_0000L, 740_000_000_000_000_0000L)

/**
 * Try to get a valid user id from the atomic reference.
 * If there is no valid user id, generate a new one.
 * @return a valid user id
 */
internal fun getValidUserId() = userUniqueId.value ?: run {
  val newUserId = generateUserId()
  val successful = userUniqueId.compareAndSet(null, newUserId)
  if (successful) {
    logger.info("USER_UNIQUE_ID(generated): $newUserId")
  }
  // Return the current value of USER_UNIQUE_ID, which may be set by another thread
  userUniqueId.value ?: newUserId
}