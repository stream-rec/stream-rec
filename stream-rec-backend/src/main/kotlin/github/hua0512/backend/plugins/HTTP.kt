package github.hua0512.backend.plugins

import github.hua0512.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureHTTP() {
  install(CORS) {
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Patch)
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.AccessControlAllowOrigin)
    allowHost("localhost:15275", schemes = listOf("http", "https"))
    logger.info("CORS enabled for $hosts")
  }
}
