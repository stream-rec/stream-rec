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

package github.hua0512.plugins.twitch.download

import github.hua0512.plugins.download.COMMON_HEADERS
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import kotlinx.serialization.json.*

private const val POST_URL = "https://gql.twitch.tv/gql"
private const val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
private const val CLIENT_ID_HEADER = "Client-Id"

internal suspend fun twitchPostQPL(client: HttpClient, json: Json, data: String, authToken: String? = null): JsonElement {
  val request = client.post(POST_URL) {
    header(CLIENT_ID_HEADER, CLIENT_ID)
    header(HttpHeaders.ContentType, "text/plain")
    // auth token is required
    authToken?.let {
      header(HttpHeaders.Authorization, "${AuthScheme.OAuth} $it")
    }
    COMMON_HEADERS.forEach { (key, value) ->
      header(key, value)
    }
    contentType(ContentType.Application.Json)
    setBody(data)
  }

  val body = request.bodyAsText()
  // check if data is an array or object
  val response = if (body.startsWith("[")) {
    json.parseToJsonElement(body).jsonArray
  } else {
    json.parseToJsonElement(body).jsonObject
  }
  // check if response has error
  if (response is JsonArray) {
    // check if response has error
    response.forEach {
      if (it.jsonObject.containsKey("errors")) {
        val error = it.jsonObject["errors"]!!.jsonArray.first().jsonObject
        val message = error["message"]!!.jsonPrimitive.content
        throw IllegalStateException("Error: $message")
      }
    }
  } else {
    if (response.jsonObject.containsKey("errors")) {
      val error = response.jsonObject["errors"]!!.jsonArray.first().jsonObject
      val message = error["message"]!!.jsonPrimitive.content
      throw IllegalStateException("Error: $message")
    }
  }
  return response
}

internal fun buildPersistedQueryRequest(operationName: String, sha256Hash: String, variables: JsonObject): String {
  return """
        {
        "operationName": "$operationName",
        "extensions": {
            "persistedQuery": {
            "version": 1,
            "sha256Hash": "$sha256Hash"
            }
        },
        "variables": $variables
        }
    """.trimIndent()
}