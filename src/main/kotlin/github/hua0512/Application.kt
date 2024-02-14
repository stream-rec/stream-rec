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

import github.hua0512.app.AppComponent
import github.hua0512.app.DaggerAppComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("Main")

class Application {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      runBlocking {
        val appComponent: AppComponent = DaggerAppComponent.create()
        val appConfig = appComponent.getAppConfig()

        val result = appConfig.initConfig()
        if (!result) {
          logger.error("Failed to initialize config: {}", result)
          return@runBlocking
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
  }
}


