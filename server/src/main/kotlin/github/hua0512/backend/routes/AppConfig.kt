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
import github.hua0512.data.config.AppConfig
import github.hua0512.repo.AppConfigRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * @author hua0512
 * @date : 2024/3/7 0:06
 */

fun Route.configRoute(repo: AppConfigRepo) {
  route("/config") {
    get {
      try {
        val appConfig = repo.getAppConfig()
        call.respond(appConfig)
      } catch (e: Exception) {
        logger.error("Failed to get app config", e)
        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal server error")
      }
    }

    put {
      try {
        val appConfig = call.receive<AppConfig>()
        repo.saveAppConfig(appConfig)
        call.respond(HttpStatusCode.OK)
      } catch (e: Exception) {
        logger.error("Failed to update app config", e)
        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal server error")
      }
    }
  }
}