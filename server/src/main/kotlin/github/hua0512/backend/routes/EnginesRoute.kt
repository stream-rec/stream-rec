/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
      call.respond(engineConfig!!)
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