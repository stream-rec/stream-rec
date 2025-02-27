package github.hua0512.backend.plugins

import filesRoute
import github.hua0512.backend.routes.*
import github.hua0512.plugins.base.IExtractorFactory
import github.hua0512.repo.AppConfigRepo
import github.hua0512.repo.UserRepo
import github.hua0512.repo.config.EngineConfigRepo
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.repo.upload.UploadRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.autohead.*
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
  extractorFactory: IExtractorFactory,
  engineConfigRepo: EngineConfigRepo,
) {
  install(AutoHeadResponse)

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
        serverRoute()
        configRoute(appConfigRepo)
        statsRoute(statsRepo)
        streamerRoute(streamerRepo)
        streamsRoute(json, streamDataRepo)
        uploadRoute(json, uploadRepo)
        filesRoute(streamDataRepo)
        extractorRoutes(extractorFactory, json)
        enginesRoute(engineConfigRepo)
      }
    }

  }
}

