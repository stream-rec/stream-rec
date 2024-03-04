package github.hua0512.backend.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
  install(ContentNegotiation) {
    json(Json {
      prettyPrint = true
      encodeDefaults = false
      isLenient = true
    })
  }
  routing {
    get("/json/kotlinx-serialization") {
      call.respond(mapOf("hello" to "world"))
    }
  }
}
