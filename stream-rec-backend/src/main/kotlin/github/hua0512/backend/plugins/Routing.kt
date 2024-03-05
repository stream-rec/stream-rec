package github.hua0512.backend.plugins

import github.hua0512.backend.routes.statsRoute
import github.hua0512.backend.routes.streamsRoute
import github.hua0512.backend.routes.streamerRoute
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.streamer.StreamDataRepo
import github.hua0512.repo.streamer.StreamerRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(streamerRepo: StreamerRepo, streamDataRepo: StreamDataRepo, statsRepo: SummaryStatsRepo) {
  install(StatusPages) {
    exception<Throwable> { call, cause ->
      call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
    }
  }
  routing {
    get("/") {
      call.respondText("Hello World!")
    }
    route("/api") {
      statsRoute(statsRepo)
      streamerRoute(streamerRepo)
      streamsRoute(streamDataRepo)
    }
  }
}

