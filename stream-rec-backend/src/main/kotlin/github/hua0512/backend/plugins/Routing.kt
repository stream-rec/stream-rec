package github.hua0512.backend.plugins

import github.hua0512.backend.routes.*
import github.hua0512.repo.AppConfigRepo
import github.hua0512.repo.UserRepo
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.repo.upload.UploadRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.configureRouting(
  json: Json,
  userRepo: UserRepo,
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
      userRoute(json, userRepo)
      authenticate("auth-jwt") {
        configRoute(appConfigRepo)
        statsRoute(statsRepo)
        streamerRoute(streamerRepo)
        streamsRoute(json, streamDataRepo)
        uploadRoute(json, uploadRepo)
      }
    }

  }
}

