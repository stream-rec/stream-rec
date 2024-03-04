package github.hua0512.backend.plugins

import github.hua0512.backend.routes.streamerRoute
import github.hua0512.logger
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.streamer.StreamerRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(streamerRepo: StreamerRepo, statsRepo: SummaryStatsRepo) {
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
    }
  }
}


fun Route.statsRoute(statsRepo: SummaryStatsRepo) {
  get("/stats") {
    val dateStartString = call.request.queryParameters["dateStart"]
    val dateEndString = call.request.queryParameters["dateEnd"]

    val dateStartEpoch = dateStartString?.toLong() ?: 0
    val dateEndEpoch = dateEndString?.toLong() ?: Long.MAX_VALUE

    val stats = statsRepo.getSummaryStatsFromTo(dateStartEpoch, dateEndEpoch)
    logger.info("Stats: $stats")
    call.respond(stats)
  }
}
