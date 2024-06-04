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

import github.hua0512.data.StreamDataId
import github.hua0512.data.StreamerId
import github.hua0512.logger
import github.hua0512.repo.stream.StreamDataRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * @author hua0512
 * @date : 2024/3/5 11:58
 */

fun Route.streamsRoute(json: Json, streamsRepo: StreamDataRepo) {
  route("/streams") {
    get {
      val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
      val pageSize = call.request.queryParameters["per_page"]?.toIntOrNull() ?: 10
      val filter = call.request.queryParameters["filter"]
      val streamers = call.request.queryParameters.getAll("streamer")?.run {
        mapNotNull {
          it.toLongOrNull()?.let { StreamerId(it) } ?: run {
            logger.warn("Invalid stream id: $it")
            null
          }
        }
      }
      val dateStart = call.request.queryParameters["date_start"]?.toLongOrNull()
      val dateEnd = call.request.queryParameters["date_end"]?.toLongOrNull()
      val sortColumn = call.request.queryParameters["sort"]
      val order = call.request.queryParameters["order"]?.uppercase()

      try {
        val count = streamsRepo.count(streamers, filter, dateStart, dateEnd).run {
          (this + pageSize - 1) / pageSize
        }
        val results = streamsRepo.getStreamDataPaged(page, pageSize, streamers, filter, dateStart, dateEnd, sortColumn, order)

        val body = buildJsonObject {
          put("pages", count)
          put("data", json.encodeToJsonElement(results))
        }
        call.respond(HttpStatusCode.OK, body)
      } catch (e: Exception) {
        logger.error("Failed to get stream data", e)
        call.respond(HttpStatusCode.InternalServerError, "Failed to get stream data")
      }
    }

    get("{id}") {
      val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
      val streamData = streamsRepo.getStreamDataById(StreamDataId(id))
      if (streamData == null) {
        call.respond(HttpStatusCode.NotFound, "Stream data not found")
      } else {
        call.respond(streamData)
      }
    }

    delete("{id}") {
      val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
      try {
        streamsRepo.delete(StreamDataId(id))
      } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, "Failed to delete stream data")
        return@delete
      }
      call.respond(HttpStatusCode.OK, "Stream data deleted")
    }
  }
}