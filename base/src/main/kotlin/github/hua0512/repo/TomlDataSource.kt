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

package github.hua0512.repo

import github.hua0512.data.config.AppConfig

/**
 * This interface defines the operations for a TOML data source.
 * TOML (Tom's Obvious, Minimal Language) is a configuration file format that's easy to read due to its clear syntax.
 * This interface provides methods to retrieve the application configuration and the path of the default TOML file.
 *
 * @deprecated Configuration via TOML file's been removed since 0.5.0 version.
 * @author hua0512
 * @date : 2024/2/18 23:49
 */
interface TomlDataSource {

  companion object {

    /**
     * This method retrieves the default path for the TOML configuration file.
     * It first tries to get the path from the "CONFIG_PATH" environment variable.
     * If the environment variable is not set, it returns the path to a "config.toml" file in the current working directory.
     *
     * @return the default path for the TOML configuration file
     */
    fun getDefaultTomlPath(): String {
      return System.getenv("CONFIG_PATH") ?: (System.getProperty("user.dir") + "/config.toml")
    }

  }


  /**
   * This method retrieves the application configuration from the TOML file.
   * The configuration is returned as an instance of the AppConfig data class.
   *
   * @return the application configuration
   */
  suspend fun getAppConfig(): AppConfig

  /**
   * This method retrieves the path of the default TOML file.
   * The path is returned as a string.
   *
   * @return the path of the default TOML file.
   */
  suspend fun getPath(): String
}