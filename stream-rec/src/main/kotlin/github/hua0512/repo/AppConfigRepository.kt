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
import github.hua0512.repo.streamer.StreamerRepo
import github.hua0512.services.FileWatcherService
import github.hua0512.utils.withIOContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author hua0512
 * @date : 2024/2/19 0:42
 */
class AppConfigRepository(
  private val localDataSource: LocalDataSource,
  private val tomlDataSource: TomlDataSource,
  private val streamerRepo: StreamerRepo,
) {

  private lateinit var fileWatcherService: FileWatcherService

  companion object {

    private val logger: Logger = LoggerFactory.getLogger(AppConfigRepository::class.java)
    var path: String = ""
  }

  suspend fun getAppConfig(): AppConfig {
    return withIOContext {
      try {
        // prioritize toml data source over local data source
        tomlDataSource.getAppConfig().also { appConfig ->
          path = tomlDataSource.getPath()

          // save to local data source
          localDataSource.saveAppConfig(appConfig)

          // update streamers
          val streamers = appConfig.streamers
          streamers.forEach {
            streamerRepo.insertOrUpdate(it)
          }
        }
      } catch (e: Exception) {
        logger.error("Failed to get app config from toml data source, falling back to local", e)
        // if toml data source fails, fallback to local data source
        val localAppConfig = localDataSource.getAppConfig()?.also {
          path = localDataSource.getPath()
        }
        // if local data source fails, return a new instance of AppConfig
        localAppConfig ?: AppConfig().also {
          logger.warn("Failed to get app config from local data source, returning a new instance of AppConfig")
        }
      }
    }
  }

  fun getFileWatcherService(): FileWatcherService? {
    if (path.isEmpty()) {
      logger.debug("Path is empty, probably because we are using a local data source")
      return null
    }
    if (::fileWatcherService.isInitialized) {
      return fileWatcherService
    }
    return FileWatcherService(path).also {
      fileWatcherService = it
    }
  }


}