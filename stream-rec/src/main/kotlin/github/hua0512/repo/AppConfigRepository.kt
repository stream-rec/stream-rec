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
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.flow.Flow
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * App config repository.
 *
 * Configuration via TOML file's been removed since 0.5.0 version.
 * Now it's only possible to configure the app via web interface.
 * @author hua0512
 * @date : 2024/2/19 0:42
 */
class AppConfigRepository(
  private val localDataSource: LocalDataSource,
) : AppConfigRepo {

  companion object {

    private val logger: Logger = LoggerFactory.getLogger(AppConfigRepository::class.java)
  }

  override suspend fun getAppConfig(): AppConfig {
    return withIOContext {
      try {
        localDataSource.getAppConfig()
      } catch (e: Exception) {
        logger.error("Failed to get app config from local data source, falling back to default", e)
        AppConfig()
      }
    }
  }

  override suspend fun saveAppConfig(appConfig: AppConfig) {
    withIOContext {
      localDataSource.saveAppConfig(appConfig)
    }
  }

  override suspend fun streamAppConfig(): Flow<AppConfig> {
    return localDataSource.streamAppConfig()
  }
}