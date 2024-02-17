/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
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

package github.hua0512.app

import github.hua0512.data.Streamer
import github.hua0512.data.StreamingPlatform
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DouyinDownloadConfig
import github.hua0512.data.config.HuyaDownloadConfig
import github.hua0512.plugins.download.Douyin
import github.hua0512.plugins.download.Huya
import github.hua0512.services.FileWatcherService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class App {

  companion object {
    @JvmStatic
    val logger = LoggerFactory.getLogger(App::class.java)

    fun getConfigPath(): String {
      return System.getenv("CONFIG_PATH") ?: (System.getProperty("user.dir") + "/config.toml")
    }
  }

  val json by lazy {
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      encodeDefaults = false
      prettyPrint = true
    }
  }

  val client by lazy {
    HttpClient(CIO) {
      engine {
        pipelining = true
      }
      install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.NONE
      }
      BrowserUserAgent()
      install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 5)
        exponentialDelay()
      }

//      install(HttpCookies) {
//        storage = AcceptAllCookiesStorage()
//      }

      install(HttpTimeout) {
        requestTimeoutMillis = 5000
        connectTimeoutMillis = 5000
        socketTimeoutMillis = 30.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
      }
      install(WebSockets) {
        pingInterval = 10_000
      }
    }
  }

  val fileWatcherService by lazy {
    FileWatcherService(getConfigPath())
  }

  var config: AppConfig
    get() = appFlow.value ?: throw Exception("App config not initialized")
    set(value) {
      val previous = appFlow.value
      val isChanged = previous != value
      if (isChanged) {
        logger.info("App config changed : {}", value)
      }
      appFlow.value = value
    }

  val appFlow = MutableStateFlow<AppConfig?>(null)

  val streamersFlow = appFlow.map { it?.streamers ?: emptyList() }

  val ffmepgPath = (System.getenv("FFMPEG_PATH") ?: "ffmpeg").run {
    // check if is windows
    if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
      "$this.exe"
    } else {
      this
    }
  }

  // semaphore to limit the number of concurrent downloads
  lateinit var downloadSemaphore: Semaphore

  suspend fun initConfig(): AppConfig {
    logger.debug("Parsing new app config...")

    val configPath = getConfigPath()

    val content = try {
      withContext(Dispatchers.IO) {
        Files.readString(Path.of(configPath))
      }
    } catch (e: Exception) {
      logger.error("Failed to read config file: {}", e.message)
      throw e
    }

    val toml = Toml {
      explicitNulls = false
      // is not needed
//      serializersModule = SerializersModule {
//        polymorphic(DownloadConfig::class) {
//          subclass(HuyaDownloadConfig::class)
//          subclass(DouyinDownloadConfig::class)
//        }
//      }
    }
    val parsedConfig = withContext(Dispatchers.IO) {
      toml.decodeFromString<AppConfig>(content).run {
        // correct the streamers
        copy(streamers = correctStreamers(this))
      }
    }
    config = parsedConfig
    downloadSemaphore = Semaphore(config.maxConcurrentDownloads)
    return config
  }


  /**
   * TODO : Use custom serializer to handle the platform and downloadConfig
   */
  private fun correctStreamers(appConfig: AppConfig): List<Streamer> {
    return appConfig.streamers.map { streamer ->
      val patterns = mapOf(
        StreamingPlatform.HUYA to Huya.REGEX,
        StreamingPlatform.DOUYIN to Douyin.REGEX
      )
      val platform = when {
        patterns[StreamingPlatform.HUYA]!!.toRegex().matches(streamer.url) -> StreamingPlatform.HUYA
        patterns[StreamingPlatform.DOUYIN]!!.toRegex().matches(streamer.url) -> StreamingPlatform.DOUYIN
        else -> throw Exception("Invalid url")
      }
      val downloadConfig = streamer.downloadConfig ?: when (platform) {
        StreamingPlatform.HUYA -> HuyaDownloadConfig()
        StreamingPlatform.DOUYIN -> DouyinDownloadConfig()
        StreamingPlatform.UNKNOWN -> TODO()
      }
      streamer.copy(
        platform = platform,
        downloadConfig = downloadConfig
      )
    }
  }
}