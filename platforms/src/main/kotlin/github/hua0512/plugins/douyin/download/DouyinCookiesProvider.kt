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

import github.hua0512.plugins.base.exceptions.InvalidExtractionParamsException
import github.hua0512.plugins.douyin.download.DouyinApis.Companion.LIVE_DOUYIN_URL
import github.hua0512.plugins.download.COMMON_HEADERS
import github.hua0512.utils.generateRandomString
import github.hua0512.utils.logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.parseClientCookiesHeader
import io.ktor.http.setCookie
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

/**
 * Files that contain the cookies used in Douyin requests.
 * @author hua0512
 * @date : 2024/10/6 22:01
 */


// cookie parameters
private var acNonce = atomic<String?>(null)
private var ttWid = atomic<String?>(null)
private var odinTT = atomic<String?>(null)
private var userUniqueId = atomic<Long?>(null)

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
  return acNonce.value ?: run {
    val newNonce = generateRandomString(21, noUpperLetters = true, noNumeric = false)
    val successful = acNonce.compareAndSet(null, newNonce)
    if (successful) {
      logger.info("$AC_NONCE_COOKIE(generated): $newNonce")
    }
    // Return the current value of NONCE, which may be set by another thread
    acNonce.value ?: newNonce
  }
}

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
    getOrPut(TT_WID_COOKIE) { getDouyinTTwid(client) }
    getOrPut(ODIN_TT_COOKIE) { generateOdinTT() }
    getOrPut(AC_NONCE_COOKIE) { generateNonce() }
  }

  return map.entries.joinToString("; ") { "${it.key}=${it.value}" } + ";"
}

private fun generateOdinTT(): String = odinTT.value ?: run {
  val newOdinTT = generateRandomString(160, noNumeric = false, noUpperLetters = true)
  val successful = odinTT.compareAndSet(null, newOdinTT)
  if (successful) {
    logger.info("$ODIN_TT_COOKIE(generated): $newOdinTT")
  }
  // Return the current value of ODIN_TT, which may be set by another thread
  odinTT.value ?: newOdinTT
}

/**
 * Makes a request to the Douyin API to get the `ttwid` parameter from the cookies.
 *
 * @param client The HTTP client to use for making requests
 * @return The `ttwid` parameter from the Douyin cookies
 */
private suspend fun getDouyinTTwid(client: HttpClient): String = ttWid.value ?: run {
  val ttwid = fetchCookiesSemaphore.withPermit {
    ttWid.value?.let { return it }

    val response = client.get("${LIVE_DOUYIN_URL}/") {
      fillDouyinCommonParams()
      COMMON_HEADERS.forEach { (key, value) ->
        header(key, value)
      }
      header(HttpHeaders.Referrer, LIVE_DOUYIN_URL)
    }
    val cookiesList = response.setCookie()
    logger.debug("cookies: {}", cookiesList)

    cookiesList.firstOrNull { it.name == TT_WID_COOKIE }?.value
      ?: throw InvalidExtractionParamsException("failed to get $TT_WID_COOKIE from web")
  }
  val successful = ttWid.compareAndSet(null, ttwid)
  if (successful) {
    logger.info("$TT_WID_COOKIE(web): $ttwid")
  }
  // Return the current value of TT_WID, which may be set by another thread
  ttWid.value ?: ttwid
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