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
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * @author hua0512
 * @date : 2024/9/27 12:17
 */

interface IHttpClientFactory {

  fun getClient(json: Json, installTimeout: Boolean = true, installWebSockets: Boolean = true): HttpClient
}

class HttpClientFactory : IHttpClientFactory {

  override fun getClient(json: Json, installTimeout: Boolean, installWebSockets: Boolean): HttpClient {
    return HttpClient(OkHttp) {
      engine {
        pipelining = true
        setProxy<OkHttpConfig>()

        config {
          // Workaround for: https://youtrack.jetbrains.com/issue/KTOR-6266/OkHttp-Remove-the-default-WebSocket-extension-header-Sec-WebSocket-Extensions
          addInterceptor(RemoveWebSocketExtensionsInterceptor())

          if (installTimeout) {
            connectTimeout(15, TimeUnit.SECONDS)
            writeTimeout(20, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
          }
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
        deflate(1.0F)
      }

//      install(HttpCookies) {
//        storage = AcceptAllCookiesStorage()
//      }

      if (installTimeout) {
        install(HttpTimeout) {
          requestTimeoutMillis = 15000
          connectTimeoutMillis = 15000
          socketTimeoutMillis = 60.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
        }
      }

      if (installWebSockets) {
        install(WebSockets)
      }
    }
  }

  private fun <T : HttpClientEngineConfig> HttpClientEngineConfig.setProxy() {
    val proxies = listOf("HTTP_PROXY", "HTTPS_PROXY", "SOCKS_PROXY").mapNotNull { env ->
      System.getenv(env)?.let { env to Url(it) }
    }

    proxies.firstOrNull()?.let { (type, url) ->
      logger.info("Using $type proxy: {}", url)
      proxy = when (type) {
        "HTTP_PROXY", "HTTPS_PROXY" -> ProxyBuilder.http(url)
        "SOCKS_PROXY" -> ProxyBuilder.socks(url.host, url.port)
        else -> null
      }
    }
  }


}


