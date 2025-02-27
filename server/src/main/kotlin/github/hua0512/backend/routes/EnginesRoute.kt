package github.hua0512.backend.routes

import github.hua0512.backend.logger
import github.hua0512.data.config.engine.EngineConfig
import github.hua0512.repo.config.EngineConfigRepo
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Defines the routes for managing engine configurations.
 *
 * @param engineConfigManager The repository for managing engine configurations.
 */
fun Route.enginesRoute(engineConfigManager: EngineConfigRepo) {

  route("/{globalId}/engines") {
    /**
     * Handles GET requests to retrieve the engine configuration for a specific engine type.
     *
     * @param engineType The type of the engine to retrieve the configuration for.
     * @param globalId The global ID of the engine configuration.
     */
    get("{engineType}") {
      val engineType =
        call.parameters["engineType"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing engine type")
      val globalId = call.parameters["globalId"]?.toIntOrNull() ?: return@get call.respond(
        HttpStatusCode.BadRequest,
        "Invalid global ID"
      )
      val engineConfig = try {
        engineConfigManager.getEngineConfig<EngineConfig>(globalId, engineType)
      } catch (e: Exception) {
        logger.error(e.message)
        return@get call.respond(HttpStatusCode.InternalServerError, e.message.toString())
      }
      call.respond(engineConfig)
    }

    /**
     * Handles PUT requests to update the engine configuration for a specific engine type.
     *
     * @param engineType The type of the engine to update the configuration for.
     * @param globalId The global ID of the engine configuration.
     * @param engineConfig The new engine configuration to update.
     * @return A response indicating the result of the update operation.
     */
    put("/{engineType}") {
      val engineType =
        call.parameters["engineType"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing engine type")
      val globalId = call.parameters["globalId"]?.toIntOrNull() ?: return@put call.respond(
        HttpStatusCode.BadRequest,
        "Invalid global ID"
      )
      val engineConfig = call.receive<EngineConfig>()
      val newConfig = engineConfigManager.updateEngineConfig(globalId, engineConfig)
      logger.info("Updated engine config: $newConfig")
      call.respond(HttpStatusCode.OK)
    }
  }
}