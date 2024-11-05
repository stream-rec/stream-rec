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
import ch.qos.logback.classic.filter.LevelFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.util.FileSize
import github.hua0512.app.AppComponent
import github.hua0512.app.DaggerAppComponent
import github.hua0512.repo.LocalDataSource
import github.hua0512.utils.executeProcess
import github.hua0512.utils.mainLogger
import github.hua0512.utils.nonEmptyOrNull
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.system.exitProcess


class Application {
  companion object {

    private const val APP_NAME = "stream-rec"

    init {
      initLogger()
    }

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
      // start the app
      val appComponent: AppComponent = DaggerAppComponent.create()
      val applicationScope = appComponent.applicationScope()

      val initializer = appComponent.initializer().apply {
        load(appComponent)
      }

      // add shutdown hook
      Runtime.getRuntime().addShutdownHook(Thread {
        mainLogger.info("{} shutting down...", APP_NAME)
        initializer.destroy()
        applicationScope.cancel()
        cancel()
      })


      // block until the app is stopped
      try {
        awaitCancellation()
      } finally {
        mainLogger.info("{} stopped", APP_NAME)
        exitProcess(0)
      }
    }


    private val LOG_LEVEL
      get() = System.getenv("LOG_LEVEL")?.nonEmptyOrNull().let { Level.valueOf(it) } ?: Level.INFO

    private fun initLogger() {
      val logLevel = LOG_LEVEL
      val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
      loggerContext.reset()

      val patternEncoder = PatternLayoutEncoder().apply {
        context = loggerContext
        pattern = "%d{MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
        start()
      }

      val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
        context = loggerContext
        name = "STDOUT"
        encoder = patternEncoder
        addFilter(LevelFilter().apply {
          setLevel(logLevel)
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
          setLevel(logLevel)
          onMatch = FilterReply.ACCEPT
          onMismatch = FilterReply.DENY
        })
        start()
      }

      loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).apply {
        addAppender(consoleAppender)
        addAppender(fileAppender)
        level = logLevel
      }
    }

  }
}


