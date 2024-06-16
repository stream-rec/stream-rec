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

import github.hua0512.data.StreamerId
import github.hua0512.data.UploadDataId
import github.hua0512.data.event.UploadEvent
import github.hua0512.data.upload.UploadState
import github.hua0512.logger
import github.hua0512.plugins.event.EventCenter
import github.hua0512.repo.upload.UploadRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put


fun Route.uploadRoute(json: Json, repo: UploadRepo) {
  route("/uploads") {
    get {
      val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
      val pageSize = call.request.queryParameters["per_page"]?.toIntOrNull() ?: 10
      // filter status list
      val status = call.request.queryParameters.getAll("status")?.mapNotNull {
        try {
          UploadState.valueOf(it).value
        } catch (e: IllegalArgumentException) {
          logger.warn("Invalid status: $it")
          null
        }
      } ?: UploadState.intValues()

      // title or file path filter
      val filter = call.request.queryParameters["filter"]
      // streamer list filter
      val streamers = call.request.queryParameters.getAll("streamer")?.mapNotNull { input ->
        input.toLongOrNull()?.let { StreamerId(it) } ?: run {
          logger.warn("Invalid streamer id: $input")
          null
        }
      }
      val sortColumn = call.request.queryParameters["sort"]
      val order = call.request.queryParameters["order"]?.uppercase()
      try {
        val count = repo.countAllUploadData(status, filter, streamers).run {
          if (this % pageSize == 0L) this / pageSize else this / pageSize + 1
        }
        val results = repo.getAllUploadDataPaginated(page, pageSize, status, filter, streamers, sortColumn, order)

        val body = buildJsonObject {
          put("pages", count)
          put("data", json.encodeToJsonElement(results))
        }
        call.respond(HttpStatusCode.OK, body)
      } catch (e: Exception) {
        logger.error("Failed to get upload data : ${e.message}")
        call.respond(HttpStatusCode.InternalServerError, "Failed to get upload data : ${e.message}")
        return@get
      }
    }

    get("{id}") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "Invalid id")
        return@get
      }
      try {
        val uploadData = repo.getUploadData(UploadDataId(id))
        if (uploadData == null) {
          call.respond(HttpStatusCode.NotFound, "Upload data not found")
        } else {
          call.respond(uploadData)
        }
      } catch (e: Exception) {
        logger.error("Failed to get upload data : ${e.message}")
        call.respond(HttpStatusCode.InternalServerError, "Failed to get upload data : ${e.message}")
      }
    }

    get("{id}/results") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "Invalid id")
        return@get
      }
      try {
        val results = repo.getUploadDataResults(UploadDataId(id))
        call.respond(results)
      } catch (e: Exception) {
        logger.error("Failed to get upload results : ${e.message}")
        call.respond(HttpStatusCode.InternalServerError, "Failed to get upload results : ${e.message}")
      }
    }

    post("{id}/retry") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "Invalid id")
        return@post
      }
      try {
        val uploadData = repo.getUploadData(UploadDataId(id))
        if (uploadData == null) {
          call.respond(HttpStatusCode.NotFound, "Upload data not found")
          return@post
        }

        call.respond(
          EventCenter.sendEvent(
            UploadEvent.UploadRetriggered(
              uploadData,
              uploadData.filePath,
              uploadData.uploadPlatform,
              Clock.System.now()
            )
          ) == true
        )
      } catch (e: Exception) {
        logger.error("Failed to retrigger upload data : ${e.message}")
        call.respond(HttpStatusCode.InternalServerError, "Failed to retrigger upload data : ${e.message}")
      }
    }


    delete("{id}") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest)
        return@delete
      }
      val uploadData = try {
        repo.getUploadData(UploadDataId(id))
      } catch (e: Exception) {
        logger.error("Delete upload data failed : ${e.message}")
        call.respond(HttpStatusCode.InternalServerError, "Failed to get upload data : ${e.message}")
        return@delete
      }

      if (uploadData == null) {
        call.respond(HttpStatusCode.NotFound, "Upload data not found")
        return@delete
      }

      try {
        repo.deleteUploadData(uploadData)
      } catch (e: Exception) {
        logger.error("Failed to delete upload data : ${e.message}")
        call.respond(HttpStatusCode.InternalServerError, "Failed to delete upload result : ${e.message}")
        return@delete
      }
      call.respond(HttpStatusCode.OK)
    }
  }
}