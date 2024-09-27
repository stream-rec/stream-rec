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

package github.hua0512.app

import github.hua0512.app.App.Companion.logger
import github.hua0512.plugins.download.COMMON_USER_AGENT
import github.hua0512.utils.RemoveWebSocketExtensionsInterceptor
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * @author hua0512
 * @date : 2024/9/27 12:17
 */

interface IHttpClientFactory {

  fun getClient(json: Json): HttpClient
}

class HttpClientFactory : IHttpClientFactory {

  override fun getClient(json: Json): HttpClient {
    return HttpClient(OkHttp) {
      engine {
        pipelining = true
        setProxy<OkHttpConfig>()

        config {
          // Workaround for: https://youtrack.jetbrains.com/issue/KTOR-6266/OkHttp-Remove-the-default-WebSocket-extension-header-Sec-WebSocket-Extensions
          addInterceptor(RemoveWebSocketExtensionsInterceptor())
        }
      }
      install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.NONE
      }

      install(UserAgent) {
        agent = COMMON_USER_AGENT
      }

      install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay()
      }

      install(ContentNegotiation) {
        json(json)
      }

      install(ContentEncoding) {
        gzip(0.9F)
      }

//      install(HttpCookies) {
//        storage = AcceptAllCookiesStorage()
//      }

      install(HttpTimeout) {
        requestTimeoutMillis = 5000
        connectTimeoutMillis = 5000
        socketTimeoutMillis = 30.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
      }
      install(WebSockets)
    }

  }

  private fun <T : HttpClientEngineConfig> HttpClientEngineConfig.setProxy() {
    // Parse proxy from ENV variable
    val httpProxy = System.getenv("HTTP_PROXY")
    val httpsProxy = System.getenv("HTTPS_PROXY")
    val socksProxy = System.getenv("SOCKS_PROXY")
    if (httpProxy.isNullOrEmpty().not()) {
      val httpProxyUrl = Url(httpProxy)
      logger.info("Using HTTP proxy: {}", httpProxyUrl)
      proxy = ProxyBuilder.http(httpProxyUrl)
    } else if (httpsProxy.isNullOrEmpty().not()) {
      val httpsProxyUrl = Url(httpsProxy)
      logger.info("Using HTTPS proxy: {}", httpsProxyUrl)
      proxy = ProxyBuilder.http(httpsProxyUrl)
    } else if (socksProxy.isNullOrEmpty().not()) {
      val socksProxyUrl = Url(socksProxy)
      logger.info("Using SOCKS proxy: {}", socksProxyUrl)
      proxy = ProxyBuilder.socks(socksProxyUrl.host, socksProxyUrl.port)
    }
  }


}


