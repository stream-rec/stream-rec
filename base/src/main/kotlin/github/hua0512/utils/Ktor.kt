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

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*


private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.UserAgent")

/**
 * A plugin that adds a `User-Agent` header to all requests.
 * Only adds the header if it is not already set.
 * @author hua0512
 * @date : 2024/11/17 5:00
 */
@KtorDsl
public class UserAgentConfig(public var agent: String = "Ktor http-client")

/**
 * A plugin that adds a `User-Agent` header to all requests.
 *
 * @property agent a `User-Agent` header value.
 */
public val UserAgent: ClientPlugin<UserAgentConfig> = createClientPlugin("UserAgent", ::UserAgentConfig) {

  val agent = pluginConfig.agent

  onRequest { request, _ ->
    if (request.headers[HttpHeaders.UserAgent] != null) {
//      LOGGER.trace("User-Agent header is already set for ${request.url}")
      return@onRequest
    }
    LOGGER.trace("Adding User-Agent header: agent for ${request.url}")
    request.header(HttpHeaders.UserAgent, agent)
  }
}