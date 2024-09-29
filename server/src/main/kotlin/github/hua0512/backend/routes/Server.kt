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

package github.hua0512.backend.routes

import github.hua0512.backend.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Properties

private var isPropertiesLoaded = false
private var versionName = "unknown"
private var commitHash = "unknown"
private var versionCode = 10000


@Synchronized
fun loadProperties() {
  if (!isPropertiesLoaded) {
    Properties().apply {
      logger::class.java.getResourceAsStream("/server.properties").use { load(it) }
      versionName = getProperty("version", "unknown")
      commitHash = getProperty("commitHash", "unknown")
      versionCode = getProperty("commitCount")?.toIntOrNull() ?: 10000
    }
    isPropertiesLoaded = true
  }
}


/**
 * Server config route
 * @author hua0512
 * @date : 2024/9/28 21:46
 */
fun Route.serverRoute() {
  route("/config") {
    get {
      try {
        loadProperties()
        val response = buildJsonObject {
          put("versionName", versionName)
          put("versionCode", versionCode)
          put("commitHash", commitHash)
        }
        call.respond(response)
      } catch (e: Exception) {
        e.printStackTrace()
        call.respond(HttpStatusCode.InternalServerError, "Failed to load properties")
      }
    }
  }
}