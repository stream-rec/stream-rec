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

package github.hua0512

import app.cash.sqldelight.db.SqlDriver
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.LevelFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.util.FileSize
import github.hua0512.app.App
import github.hua0512.app.AppComponent
import github.hua0512.app.DaggerAppComponent
import github.hua0512.backend.backendServer
import github.hua0512.data.config.AppConfig
import github.hua0512.repo.AppConfigRepo
import github.hua0512.repo.LocalDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.pathString


class Application {
  companion object {
    init {
      initLogger()
    }

    @JvmStatic
    fun main(args: Array<String>) {
      runBlocking {
        val appComponent: AppComponent = DaggerAppComponent.create()
        val app = appComponent.getAppConfig()
        val appConfigRepository = appComponent.getAppConfigRepository()

        var appConfig = withContext(Dispatchers.IO) {
          initAppConfig(appConfigRepository, app)
        }

        launch {
          appConfigRepository.streamAppConfig()
            .flowOn(Dispatchers.IO)
            .collect {
              appConfig = it
              logger.info("App config updated: $it")
              app.config = it
              app.downloadSemaphore = Semaphore(it.maxConcurrentDownloads)
            }
        }

        val downloadService = appComponent.getDownloadService()
        val uploadService = appComponent.getUploadService()

        launch {
          downloadService.run()
        }
        launch(Dispatchers.IO) {
          uploadService.run()
        }

        // start server
        val server = backendServer(
          appComponent.getStreamerRepo(),
          appComponent.getStreamDataRepo(),
          appComponent.getStatsRepository(),
          appComponent.getUploadRepo(),
        ).apply {
          start()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
          logger.info("Shutting down...")
          server.stop(1000, 1000)
          appComponent.getSqlDriver().closeDriver()
          app.releaseAll()
          cancel("Application is shutting down")
          logger.info("Shutdown complete")
        })
      }
    }

    private fun initLogger() {
      val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
      loggerContext.reset()

      val patternEncoder = PatternLayoutEncoder().apply {
        context = loggerContext
        pattern = "%d{YYYY-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
        start()
      }

      val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
        context = loggerContext
        name = "STDOUT"
        encoder = patternEncoder
        addFilter(LevelFilter().apply {
          val level = System.getenv("LOG_LEVEL")?.let { Level.valueOf(it) } ?: Level.INFO
          setLevel(level)
          onMatch = FilterReply.ACCEPT
          onMismatch = FilterReply.DENY
        })
        start()
      }

      val configParentPath = LocalDataSource.getDefaultPath().run {
        Path(this).parent.parent
      }
      val logFile = configParentPath.resolve("logs/run.log").pathString
      println("Logging to : $logFile")

      val timedRollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>().apply {
        context = loggerContext
        fileNamePattern = "$logFile.%d{yyyy-MM-dd}.gz"
        maxHistory = 7
        setTotalSizeCap(FileSize.valueOf("300MB"))
      }
      val fileAppender = RollingFileAppender<ILoggingEvent>().apply {
        context = loggerContext
        name = "FILE"
        file = logFile
        encoder = patternEncoder

        rollingPolicy = timedRollingPolicy.also {
          it.setParent(this)
          it.start()
        }
        addFilter(LevelFilter().apply {
          setLevel(Level.DEBUG)
          onMatch = FilterReply.ACCEPT
          onMismatch = FilterReply.DENY
        })
        start()
      }

      loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).apply {
        addAppender(consoleAppender)
        addAppender(fileAppender)
        level = Level.DEBUG
      }
    }

    private suspend fun initAppConfig(repo: AppConfigRepo, app: App): AppConfig {
      return repo.getAppConfig().also {
        with(app) {
          config = it
          downloadSemaphore = Semaphore(it.maxConcurrentDownloads)
        }
      }
    }

    private fun SqlDriver.closeDriver() {
      try {
        close()
      } catch (e: IOException) {
        logger.error("Error closing sql driver", e)
      }
    }
  }
}


