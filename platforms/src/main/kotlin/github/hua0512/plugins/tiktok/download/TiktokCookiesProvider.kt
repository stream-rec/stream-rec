package github.hua0512.plugins.tiktok.download

import github.hua0512.app.COMMON_HEADERS
import github.hua0512.plugins.base.exceptions.InvalidExtractionParamsException
import github.hua0512.plugins.douyin.download.TT_WID_COOKIE
import github.hua0512.plugins.douyin.download.fillDouyinCommonParams
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


private var ttWid = atomic<String?>(null)

private val fetchCookiesSemaphore by lazy { Semaphore(1) }

private val logger = logger("TiktokCookiesProvider")


/**
 * Makes a request to the TikTok API to get the `ttwid` parameter from the cookies.
 *
 * @param client The HTTP client to use for making requests
 * @return The `ttwid` parameter from the TikTok cookies
 */
private suspend fun getTiktokTTwid(client: HttpClient): String = ttWid.value ?: run {
  val ttwid = fetchCookiesSemaphore.withPermit {
    ttWid.value?.let { return it }

    val response = client.get(TiktokApis.BASE_URL) {
      fillDouyinCommonParams()
      COMMON_HEADERS.forEach { (key, value) ->
        header(key, value)
      }
      header(HttpHeaders.Referrer, TiktokApis.BASE_URL)
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


/**
 * Populates the missing parameters (ttwid, __ac_nonce) in the specified Douyin cookies.
 *
 * @param cookies The Douyin cookies to populate
 * @param client The HTTP client to use for making requests
 * @return The Douyin cookies with the missing parameters populated
 */
internal suspend fun populateTiktokCookieMissedParams(cookies: String, client: HttpClient): String {
  val parsedCookies = if (cookies.isEmpty()) {
    mutableMapOf()
  } else {
    parseClientCookiesHeader(cookies).toMutableMap()
  }

  val map = parsedCookies.apply {
    getOrPut(TT_WID_COOKIE) { getTiktokTTwid(client) }
  }

  return map.entries.joinToString("; ") { "${it.key}=${it.value}" } + ";"
}