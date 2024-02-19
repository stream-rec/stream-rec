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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import github.hua0512.app.App
import github.hua0512.app.AppComponent
import github.hua0512.app.DaggerAppComponent
import github.hua0512.data.config.AppConfig
import github.hua0512.repo.AppConfigRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.pathString

val logger: Logger = LoggerFactory.getLogger("Main")

class Application {
  companion object {
    init {
      initLogger()
    }

    @OptIn(FlowPreview::class)
    @JvmStatic
    fun main(args: Array<String>) {
      runBlocking {
        val appComponent: AppComponent = DaggerAppComponent.create()
        val app = appComponent.getAppConfig()
        val appConfigRepository = appComponent.getAppConfigRepository()

        var appConfig = withContext(Dispatchers.IO) {
          initAppConfig(appConfigRepository, app)
        }

        val fileWatcherService = appConfigRepository.getFileWatcherService()?.also {
          launch {
            it.watchFileFlow().debounce(500).collect {
              logger.info("Config file changed, reloading")
              appConfig = initAppConfig(appConfigRepository, app)
            }
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

        Runtime.getRuntime().addShutdownHook(Thread {
          logger.info("Shutting down")
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
        start()
      }

      val configParentPath = System.getenv("CONFIG_PATH").run {
        if (this != null) {
          Path(this).parent
        } else {
          Path(System.getProperty("user.dir"))
        }
      }
      val logFile = configParentPath.resolve("logs/run.log").pathString
      println("Logging to : $logFile")

      val timedRollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>().apply {
        context = loggerContext
        fileNamePattern = "$logFile.%d{yyyy-MM-dd}.gz"
        maxHistory = 7
        setTotalSizeCap(FileSize.valueOf("100MB"))
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
        start()
      }

      val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).apply {
        addAppender(consoleAppender)
        addAppender(fileAppender)
        level = System.getenv("LOG_LEVEL")?.let { Level.valueOf(it) } ?: Level.INFO
        logger.info("Log level set to $level")
      }
    }

    private suspend fun initAppConfig(repo: AppConfigRepository, app: App): AppConfig {
      return repo.getAppConfig().also {
        app.config = it
        app.downloadSemaphore = Semaphore(it.maxConcurrentDownloads)
      }
    }


  }
}


