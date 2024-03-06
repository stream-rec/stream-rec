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

import github.hua0512.data.UploadDataId
import github.hua0512.repo.uploads.UploadRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Route.uploadRoute(repo: UploadRepo) {
  route("/uploads") {
    get {
      try {
        call.respond(repo.getAllUploadData())
      } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, "Failed to get upload results : ${e.message}")
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
        val uploadData = repo.getUploadData(UploadDataId(id))
        if (uploadData == null) {
          call.respond(HttpStatusCode.NotFound, "Upload data not found")
        } else {
          val results = repo.getUploadDataResults(UploadDataId(id))
          call.respond(results)
        }
      } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, "Failed to get upload results : ${e.message}")
      }
    }


    delete("{id}") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest)
        return@delete
      }
      try {
        repo.deleteUploadData(UploadDataId(id))
      } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, "Failed to delete upload result : ${e.message}")
        return@delete
      }
      call.respond(HttpStatusCode.OK)
    }
  }
}