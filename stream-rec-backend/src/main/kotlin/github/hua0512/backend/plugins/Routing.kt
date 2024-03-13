package github.hua0512.backend.plugins

import github.hua0512.backend.routes.*
import github.hua0512.repo.AppConfigRepo
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.streamer.StreamDataRepo
import github.hua0512.repo.streamer.StreamerRepo
import github.hua0512.repo.uploads.UploadRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.configureRouting(
  json: Json,
  appConfigRepo: AppConfigRepo,
  streamerRepo: StreamerRepo,
  streamDataRepo: StreamDataRepo,
  statsRepo: SummaryStatsRepo,
  uploadRepo: UploadRepo,
) {
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
      configRoute(appConfigRepo)
      statsRoute(statsRepo)
      streamerRoute(streamerRepo)
      streamsRoute(streamDataRepo)
      uploadRoute(json, uploadRepo)
    }
  }
}

