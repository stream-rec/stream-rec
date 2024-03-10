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

import github.hua0512.data.stream.Streamer
import github.hua0512.logger
import github.hua0512.repo.streamer.StreamerRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * @author hua0512
 * @date : 2024/3/4 13:00
 */
fun Route.streamerRoute(repo: StreamerRepo) {
  route("/streamers") {
    get {
      val filter = call.request.queryParameters["filter"] ?: "all"
      when (filter) {
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
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest)
        return@get
      }
      val streamer = repo.getStreamerById(id)
      if (streamer == null) {
        call.respond(HttpStatusCode.NotFound)
        return@get
      }
      call.respond(streamer)
    }

    post {
      val streamer: Streamer = try {
        call.receive<Streamer>().also {
          logger.info("Received streamer : {}", it)
        }
      } catch (e: Exception) {
        logger.error("Error receiving streamer", e)
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
          call.respond(HttpStatusCode.BadRequest, "Template streamer must have download config")
          return@post
        }
        repo.saveStreamer(streamer)
      } catch (e: Exception) {
        logger.error("Error saving streamer", e)
        call.respond(HttpStatusCode.BadRequest)
        return@post
      }

      val newStreamer = repo.findStreamerByUrl(streamer.url) ?: run {
        logger.error("Error saving streamer, not found in db : {}", streamer)
        call.respond(HttpStatusCode.InternalServerError, "Streamer not found in db")
        return@post
      }
      call.respond(newStreamer)
    }

    put("{id}") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "Invalid id")
        return@put
      }
      val streamer: Streamer
      try {
        streamer = call.receive<Streamer>()
        logger.info("server updating streamer : {}", streamer)
      } catch (e: Exception) {
        logger.error("Error receiving streamer", e)
        call.respond(HttpStatusCode.BadRequest, "Invalid streamer: ${e.message}")
        return@put
      }
      if (streamer.id != id) {
        call.respond(HttpStatusCode.BadRequest, "Invalid id : mismatch")
        return@put
      }
      repo.findStreamerByUrl(streamer.url) ?: run {
        logger.error("Error updating streamer, not found in db : {}", streamer)
        call.respond(HttpStatusCode.BadRequest, "Streamer not found in db")
        return@put
      }

      try {
        streamer.id = id
        repo.insertOrUpdate(streamer)
        call.respond(streamer)
      } catch (e: Exception) {
        logger.error("Error updating streamer", e)
        call.respond(HttpStatusCode.InternalServerError, "Error updating streamer: ${e.message}")
      }
    }

    delete("{id}") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "Invalid id")
        return@delete
      }
      try {
        val streamer = repo.getStreamerById(id) ?: run {
          call.respond(HttpStatusCode.NotFound, "Streamer not found")
          return@delete
        }

        if (streamer.isTemplate) {
          val count = repo.countStreamersUsingTemplate(id)
          if (count > 0) {
            call.respond(HttpStatusCode.BadRequest, "Template streamer is used by $count streamers")
            return@delete
          }
        }
        repo.deleteStreamerById(id)
      } catch (e: Exception) {
        logger.error("Error deleting streamer", e)
        call.respond(HttpStatusCode.InternalServerError, "Error deleting streamer: ${e.message}")
        return@delete
      }
      call.respond(HttpStatusCode.OK)

    }
  }
}