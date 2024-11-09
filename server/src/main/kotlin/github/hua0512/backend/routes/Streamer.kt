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
import github.hua0512.data.StreamerId
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamerState
import github.hua0512.repo.stream.StreamerRepo
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Streamer related routes
 * @author hua0512
 * @date : 2024/3/4 13:00
 */
fun Route.streamerRoute(repo: StreamerRepo) {
  route("/streamers") {
    get {
      val filter = call.request.queryParameters["filter"] ?: "all"
      when (filter) {
        "live" -> {
          val streamers = repo.getStreamersByLiveStatus(true)
          call.respond(streamers)
        }

        "offline" -> {
          val streamers = repo.getStreamersByLiveStatus(false)
          call.respond(streamers)
        }

        "active" -> {
          val streamers = repo.getStreamersActive()
          call.respond(streamers)
        }

        "inactive" -> {
          val streamers = repo.getStreamersInactive()
          call.respond(streamers)
        }

        "all" -> {
          val streamers = repo.getStreamers()
          call.respond(streamers)
        }

        "template" -> {
          val streamers = repo.getAllTemplateStreamers()
          call.respond(streamers)
        }

        "non-template" -> {
          val streamers = repo.getAllNonTemplateStreamers()
          call.respond(streamers)
        }

        else -> {
          call.respondText("Invalid filter", status = HttpStatusCode.BadRequest)
        }
      }
    }

    get("{id}") {
      val id = call.parameters["id"]?.toLongOrNull() ?: run {
        call.respond(HttpStatusCode.BadRequest)
        return@get
      }
      val streamer = repo.getStreamerById(StreamerId(id))
      if (streamer == null) {
        call.respond(HttpStatusCode.NotFound)
        return@get
      }
      call.respond(streamer)
    }

    post {
      val streamer: Streamer = try {
        call.receive<Streamer>().also {
          logger.info("Received stream : {}", it)
        }
      } catch (e: Exception) {
        logger.error("Error receiving stream", e)
        call.respond(HttpStatusCode.BadRequest)
        return@post
      }

      val dbStreamer = repo.findStreamerByUrl(streamer.url)
      if (dbStreamer != null) {
        call.respond(HttpStatusCode.BadRequest, "Streamer already exists")
        return@post
      }
      try {
        val isTemplate = streamer.isTemplate
        if (isTemplate && streamer.downloadConfig == null) {
          call.respond(HttpStatusCode.BadRequest, "Template stream must have download config")
          return@post
        }
        val saved = repo.save(streamer)
        call.respond(saved)
      } catch (e: Exception) {
        logger.error("Error saving stream", e)
        call.respond(HttpStatusCode.BadRequest)
        return@post
      }
    }

    put("{id}") {
      val id = call.parameters["id"]?.toLongOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
      val state = call.request.queryParameters["state"]?.toIntOrNull()

      if (state != null) {
        try {
          val streamer = repo.getStreamerById(StreamerId(id)) ?: return@put call.respond(
            HttpStatusCode.NotFound,
            "Streamer not found"
          )
          val newState = StreamerState.valueOf(state)
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid state")

          val newStreamer = streamer.copy(state = newState)
          val status = repo.update(newStreamer)
          if (!status) return@put call.respond(HttpStatusCode.InternalServerError, "Error updating stream state")
          call.respond(HttpStatusCode.OK, buildJsonObject {
            put("msg", "Stream state updated")
            put("code", 200)
          })
        } catch (e: Exception) {
          logger.error("Error updating stream state", e)
          call.respond(HttpStatusCode.InternalServerError, "Error updating stream state: ${e.message}")
        }
      } else {
        var streamer: Streamer = try {
          call.receive<Streamer>().also { logger.debug("Received stream : {}", it) }
        } catch (e: Exception) {
          logger.error("Error receiving stream", e)
          return@put call.respond(HttpStatusCode.BadRequest, "Invalid stream: ${e.message}")
        }

        if (streamer.id != id) return@put call.respond(HttpStatusCode.BadRequest, "Invalid id : mismatch")

        val old = repo.getStreamerById(StreamerId(id)) ?: return@put call.respond(
          HttpStatusCode.BadRequest,
          "Error updating stream, not found in db"
        )
        val dbStreamer = repo.findStreamerByUrl(streamer.url)
        if (dbStreamer != null && dbStreamer.id != id) return@put call.respond(
          HttpStatusCode.BadRequest,
          "Streamer url already exists"
        )

        if (old.isTemplate && !streamer.isTemplate) {
          val count = repo.countStreamersUsingTemplate(StreamerId(id))
          if (count > 0) return@put call.respond(
            HttpStatusCode.BadRequest,
            "Template stream is used by $count streamers"
          )
        }

        // do not allow state updates if new state is not NOT_LIVE or CANCELLED
        if (streamer.state != StreamerState.NOT_LIVE && streamer.state != StreamerState.CANCELLED) {
          // restore old state
          streamer.state = old.state
        }

        try {
          repo.update(streamer)
          call.respond(streamer)
        } catch (e: Exception) {
          logger.error("Error updating stream", e)
          call.respond(HttpStatusCode.InternalServerError, "Error updating stream: ${e.message}")
        }
      }
    }

    delete("{id}") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "Invalid id")
        return@delete
      }
      try {
        val streamer = repo.getStreamerById(StreamerId(id)) ?: run {
          call.respond(HttpStatusCode.NotFound, "Streamer not found")
          return@delete
        }

        if (streamer.isTemplate) {
          val count = repo.countStreamersUsingTemplate(StreamerId(id))
          if (count > 0) {
            call.respond(HttpStatusCode.BadRequest, "Template stream is used by $count streamers")
            return@delete
          }
        }
        repo.delete(streamer)
      } catch (e: Exception) {
        logger.error("Error deleting stream", e)
        call.respond(HttpStatusCode.InternalServerError, "Error deleting stream: ${e.message}")
        return@delete
      }
      call.respond(HttpStatusCode.OK)

    }
  }
}