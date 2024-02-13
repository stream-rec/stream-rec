package github.hua0512.app

import github.hua0512.data.Streamer
import github.hua0512.data.StreamingPlatform
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DouyinDownloadConfig
import github.hua0512.data.config.HuyaDownloadConfig
import github.hua0512.plugins.download.Douyin
import github.hua0512.plugins.download.Huya
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.Dispatchers
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

  var config: AppConfig = AppConfig()
  var isInitialized = false
  val ffmepgPath = System.getenv("FFMPEG_PATH") ?: "ffmpeg"

  // semaphore to limit the number of concurrent downloads
  lateinit var downloadSemaphore: Semaphore

  // semaphore to limit the number of concurrent uploads
  lateinit var uploadSemaphore: Semaphore

  suspend fun initConfig(): Boolean {
    logger.info("Initializing app config...")

    if (isInitialized) {
      logger.info("App config already initialized")
      return true
    }

    val configPath = System.getenv("CONFIG_PATH") ?: "config.toml"

    val content = try {
      withContext(Dispatchers.IO) {
        Files.readString(Path.of(configPath))
      }
    } catch (e: Exception) {
      logger.error("Failed to read config file: {}", e.message)
      return false
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
    uploadSemaphore = Semaphore(config.maxConcurrentUploads)
    isInitialized = true
    logger.info("App config initialized")
    logger.info("Config: {}", config)
    return true
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