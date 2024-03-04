package github.hua0512.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.engine.*

fun Application.configureAdministration() {
  install(ShutDownUrl.ApplicationCallPlugin) {
    // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
    shutDownUrl = "/ktor/application/shutdown"
    // A function that will be executed to get the exit code of the process
    exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
  }
}
