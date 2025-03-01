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

package github.hua0512.backend

import github.hua0512.backend.plugins.*
import github.hua0512.plugins.base.IExtractorFactory
import github.hua0512.plugins.event.DownloadStateEventPlugin
import github.hua0512.repo.AppConfigRepo
import github.hua0512.repo.UserRepo
import github.hua0512.repo.config.EngineConfigRepo
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.repo.upload.UploadRepo
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext


data class ServerConfig(
  val port: Int = 12555,
  val host: String = "0.0.0.0",
  val parentContext: CoroutineContext,
  val json: Json,
  val userRepo: UserRepo,
  val appConfigRepo: AppConfigRepo,
  val streamerRepo: StreamerRepo,
  val streamDataRepo: StreamDataRepo,
  val statsRepo: SummaryStatsRepo,
  val uploadRepo: UploadRepo,
  val extractorFactory: IExtractorFactory,
  val engineConfigRepo: EngineConfigRepo,
  val downloadStateEventPlugin: DownloadStateEventPlugin,
)

fun CoroutineScope.backendServer(config: ServerConfig): EmbeddedServer<ApplicationEngine, NettyApplicationEngine.Configuration> = embeddedServer(
  Netty,
  port = config.port,
  host = config.host,
  parentCoroutineContext = config.parentContext,
  module = { module(config) })


fun Application.module(serverConfig: ServerConfig) {
  configureSecurity()
  configureHTTP()
  configureMonitoring()
  configureSerialization()
  configureSockets(serverConfig.downloadStateEventPlugin)
  configureRouting(
    serverConfig.json,
    serverConfig.userRepo,
    serverConfig.appConfigRepo,
    serverConfig.streamerRepo,
    serverConfig.streamDataRepo,
    serverConfig.statsRepo,
    serverConfig.uploadRepo,
    serverConfig.extractorFactory,
    serverConfig.engineConfigRepo
  )
}
