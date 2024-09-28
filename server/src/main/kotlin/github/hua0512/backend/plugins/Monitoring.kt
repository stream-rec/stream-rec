package github.hua0512.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.*
import org.slf4j.event.*

fun Application.configureMonitoring() {
  install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/") }
    callIdMdc("call-id")
  }
  install(CallId) {
    header(HttpHeaders.XRequestId)
    verify { callId: String ->
      callId.isNotEmpty()
    }
  }
}
