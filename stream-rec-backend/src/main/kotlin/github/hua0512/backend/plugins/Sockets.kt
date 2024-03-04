package github.hua0512.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration

fun Application.configureSockets() {
  install(WebSockets) {
    pingPeriod = Duration.ofSeconds(15)
    timeout = Duration.ofSeconds(15)
    maxFrameSize = Long.MAX_VALUE
    masking = false
  }
  routing {
    webSocket("/ws") { // websocketSession
      for (frame in incoming) {
        if (frame is Frame.Text) {
          val text = frame.readText()
          outgoing.send(Frame.Text("YOU SAID: $text"))
          if (text.equals("bye", ignoreCase = true)) {
            close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
          }
        }
      }
    }
  }
}
