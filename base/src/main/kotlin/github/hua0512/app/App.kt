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

import github.hua0512.data.config.AppConfig
import io.ktor.client.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory


class App(val json: Json, val client: HttpClient) {

  companion object {
    @JvmStatic
    val logger: org.slf4j.Logger = LoggerFactory.getLogger(App::class.java)
  }

  val config: AppConfig
    get() = appFlow.value ?: throw IllegalStateException("App config not initialized")

  private val _appFlow = MutableStateFlow<AppConfig?>(null)

  val appFlow: StateFlow<AppConfig?> = _appFlow.asStateFlow()

  fun updateConfig(config: AppConfig) {
    val previous = appFlow.value
    val isChanged = previous != config
    if (isChanged) {
      logger.info("App config changed : {}", config)
    }
    this._appFlow.value = config
  }

  /**
   * Closes the HTTP client.
   */
  fun releaseAll() {
    client.close()
  }
}