package github.hua0512.backend.routes

import github.hua0512.logger
import github.hua0512.repo.stats.SummaryStatsRepo
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Route.statsRoute(statsRepo: SummaryStatsRepo) {
  get("/stats") {
    val dateStartString = call.request.queryParameters["dateStart"]
    val dateEndString = call.request.queryParameters["dateEnd"]

    val dateStartEpoch = dateStartString?.toLong() ?: 0
    val dateEndEpoch = dateEndString?.toLong() ?: Long.MAX_VALUE

    try {
      val stats = statsRepo.getSummaryStatsFromTo(dateStartEpoch, dateEndEpoch)
      call.respond(stats)
    } catch (e: Exception) {
      logger.error("Failed to get stats", e)
      call.respond("Failed to get stats, error: ${e.message}")
      return@get
    }
  }
}
