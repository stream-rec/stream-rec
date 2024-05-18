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

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import github.hua0512.backend.plugins.jwtAudience
import github.hua0512.backend.plugins.jwtDomain
import github.hua0512.backend.plugins.jwtSecret
import github.hua0512.data.event.UserEvent.UserLogin
import github.hua0512.data.user.User
import github.hua0512.logger
import github.hua0512.plugins.event.EventCenter
import github.hua0512.repo.UserRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * User route
 * @author hua0512
 * @date : 2024/3/14 19:04
 */
fun Route.userRoute(json: Json, userRepo: UserRepo) {
  route("/auth") {
    post("/login") {
      try {
        val body = call.receive<User>()
        val user = userRepo.getUserByName(body.username)
        if (user == null) {
          call.respond(HttpStatusCode.BadRequest, "User not found")
          return@post
        }
        if (user.password != body.password) {
          call.respond(HttpStatusCode.BadRequest, "Password incorrect")
          return@post
        }
        val validTo = Clock.System.now().plus(7.toDuration(DurationUnit.DAYS)).toJavaInstant()
        val token = JWT.create()
          .withAudience(jwtAudience)
          .withIssuer(jwtDomain)
          .withClaim("username", user.username)
          .withExpiresAt(validTo)
          .sign(Algorithm.HMAC256(jwtSecret))
        val responseBody = buildJsonObject {
          put("token", token)
          put("validTo", validTo.toKotlinInstant().toEpochMilliseconds())
        }
        call.respond(HttpStatusCode.OK, responseBody)
        EventCenter.sendEvent(UserLogin(user.username, Clock.System.now()))
      } catch (e: Exception) {
        logger.error("Failed to login", e)
        call.respond(HttpStatusCode.BadRequest, "Failed to get user")
        return@post
      }
    }

    post("/recover") {
      val json: JsonElement = call.receive()
      val userName = json.jsonObject["username"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, "User not found")
      val user = userRepo.getUserByName(userName) ?: return@post call.respond(HttpStatusCode.BadRequest, "User not found")
      logger.info("${user.username} password: ${user.password}")
      call.respond(HttpStatusCode.OK, "Password logged in console")
    }
  }
}