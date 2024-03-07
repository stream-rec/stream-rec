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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.Toml
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * TOML data source.
 * @deprecated Configuration via TOML file's been removed since 0.5.0 version.
 * @author hua0512
 * @date : 2024/2/18 23:51
 */
class TomlDataSourceImpl() : TomlDataSource {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TomlDataSourceImpl::class.java)
  }

  val toml by lazy {
    Toml {
      explicitNulls = false
    }
  }

  override suspend fun getAppConfig(): AppConfig {
    val configPath = getPath()

    val content = try {
      withContext(Dispatchers.IO) {
        Files.readString(Path.of(configPath))
      }
    } catch (e: Exception) {
      logger.error("Failed to read config file: {}", e.message)
      throw e
    }

    val parsedConfig = withContext(Dispatchers.IO) {
      toml.decodeFromString<AppConfig>(content)
//        .run {
        // correct the streamers
//        copy(streamers = correctStreamers(this))
//      }
    }
    return parsedConfig
  }

  override suspend fun getPath(): String = TomlDataSource.getDefaultTomlPath()


  /**
   * TODO : Use custom serializer to handle the platform and downloadConfig
   */
//  private fun correctStreamers(appConfig: AppConfig): List<Streamer> {
//    return appConfig.streamers.map { streamer ->
//      val patterns = mapOf(
//        StreamingPlatform.HUYA to Huya.REGEX,
//        StreamingPlatform.DOUYIN to Douyin.REGEX
//      )
//      val platform = when {
//        patterns[StreamingPlatform.HUYA]!!.toRegex().matches(streamer.url) -> StreamingPlatform.HUYA
//        patterns[StreamingPlatform.DOUYIN]!!.toRegex().matches(streamer.url) -> StreamingPlatform.DOUYIN
//        else -> throw Exception("Invalid url")
//      }
//      val downloadConfig = streamer.downloadConfig ?: when (platform) {
//        StreamingPlatform.HUYA -> DownloadConfig.HuyaDownloadConfig()
//        StreamingPlatform.DOUYIN -> DownloadConfig.DouyinDownloadConfig()
//        StreamingPlatform.UNKNOWN -> TODO()
//      }
//      streamer.copy(
//        platform = platform,
//        downloadConfig = downloadConfig
//      )
//    }
//  }


}