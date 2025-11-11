/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import github.hua0512.backend.logger
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.IExtractorFactory
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*


fun Route.extractorRoutes(factory: IExtractorFactory, json: Json) {

  route("/extract") {

    get {
      val url = call.request.queryParameters["url"]
      val params = call.request.queryParameters.toMap().filterKeys { it != "url" }

      logger.debug("Extracting url: {}, params: {}", url, params)

      if (url.isNullOrEmpty()) {
        call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "url parameter is required")
          put("code", 400)
        })
        return@get
      }


      val extractor =
        factory.getExtractorFromUrl(url, params) ?: return@get call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "No extractor found for the given url")
          put("code", 404)
        }.also {
          logger.error("no extractor found: $it")
        })

      logger.debug("using extractor: {}", extractor)

      val initResult = extractor.prepare()
      if (initResult.isErr) {
        return@get call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "Failed to initialize extractor, ${initResult.getError()}")
          put("code", 500)
        })
      }

      val extractResult = extractor.extract()
      if (extractResult.isErr) {
        return@get call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "Failed to extract data, ${extractResult.getError()}")
          put("code", 500)
        })
      }

      val mediaInfo = extractResult.get() ?: return@get call.respond(HttpStatusCode.OK, buildJsonObject {
        put("msg", "No media info found")
        put("code", 404)
      })

      return@get call.respond(HttpStatusCode.OK, buildJsonObject {
        put("msg", "success")
        put("code", 200)
        put("data", json.encodeToJsonElement<MediaInfo>(MediaInfo.serializer(), mediaInfo))
        put("headers", json.encodeToString(extractor.getRequestHeaders()))
      }.also {
        logger.info("extracted : $it")
      })
    }

    post("/getTrueUrl") {
      val body = call.receive<JsonObject>()
      val url = body["url"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.OK, buildJsonObject {
        put("msg", "url parameter is required")
        put("code", 400)
      })

      val data: StreamInfo = body["data"]?.let {
        json.decodeFromJsonElement(StreamInfo.serializer(), it)
      } ?: return@post call.respond(HttpStatusCode.OK, buildJsonObject {
        put("msg", "data parameter is required")
        put("code", 400)
      })

      // only huya platform needs to call getTrueUrl at the moment
      if (!url.contains("huya.com")) {
        return@post call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "success")
          put("code", 200)
          put("data", json.encodeToJsonElement(StreamInfo.serializer(), data))
          put("headers", json.encodeToString(emptyMap<String, String>()))
        }.also {
          logger.debug("no need to call getTrueUrl: {}", it)
        })
      }

      val extractor =
        factory.getExtractorFromUrl(url, emptyMap()) ?: return@post call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "No extractor found for the given url")
          put("code", 404)
        }.also {
          logger.error("true url extractor not found: $it")
        })

      val initResult = extractor.prepare()
      if (initResult.isErr) {
        return@post call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "Failed to initialize extractor, ${initResult.getError()}")
          put("code", 500)
        })
      }

      val trueUrl = extractor.getTrueUrl(data)

      if (trueUrl.isErr) {
        return@post call.respond(HttpStatusCode.OK, buildJsonObject {
          put("msg", "Failed to get true url, ${trueUrl.getError()}")
          put("code", 500)
        })
      }

      return@post call.respond(HttpStatusCode.OK, buildJsonObject {
        put("msg", "success")
        put("code", 200)
        put("data", json.encodeToJsonElement(StreamInfo.serializer(), trueUrl.get()!!))
        put("headers", json.encodeToString(extractor.getRequestHeaders()))
      }.also {
        logger.debug("true url : {}", it)
      })
    }

  }
}