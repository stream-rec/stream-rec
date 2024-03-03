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

import github.hua0512.dao.AppConfigDao
import github.hua0512.dao.stream.StreamerDao
import github.hua0512.data.VideoFormat
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DouyinConfigGlobal
import github.hua0512.data.config.HuyaConfigGlobal
import github.hua0512.utils.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * @author hua0512
 * @date : 2024/2/18 23:55
 */
class LocalDataSourceImpl(private val dao: AppConfigDao, private val json: Json, private val streamerDao: StreamerDao) : LocalDataSource {

  override suspend fun getAppConfig(): AppConfig? {
    return withIOContext {
      dao.getLatestAppConfig()?.let { config ->
        val id = config.id

        val huyaConfig = config.huyaConfig?.run {
          json.decodeFromString<HuyaConfigGlobal>(this)
        } ?: HuyaConfigGlobal()

        val douyinConfig = config.douyinConfig?.run {
          json.decodeFromString<DouyinConfigGlobal>(this)
        } ?: DouyinConfigGlobal()

        val streamers = streamerDao.getAllStreamers().map { it.toStreamer(json) }

        AppConfig(
          config.engine ?: "ffmpeg",
          config.danmu!!.boolean,
          config.outputFolder!!,
          config.outputFileName!!,
          VideoFormat.format(config.outputFileFormat!!) ?: VideoFormat.flv,
          config.minPartSize ?: 20000000,
          config.maxPartSize ?: 2621440000,
          config.maxPartDuration,
          config.maxDownloadRetries?.toInt() ?: 3,
          config.downloadRetryDelay ?: 10,
          config.maxConcurrentDownloads?.toInt() ?: 5,
          config.maxConcurrentUploads?.toInt() ?: 3,
          config.deleteFilesAfterUpload?.boolean ?: true,
          huyaConfig,
          douyinConfig,
          streamers,
        ).also {
          it.id = id
        }
      }
    }
  }

  override fun getPath(): String {
    val configPath = TomlDataSource.getDefaultTomlPath()
    val path = Path(configPath).parent.resolve("db/stream-rec.db")
    return path.pathString
  }

  override suspend fun saveAppConfig(appConfig: AppConfig) {
    return appConfig.run {
      dao.upsert(
        AppConfigEntity(
          1L,
          engine,
          danmu.asLong,
          outputFolder,
          outputFileName,
          outputFileFormat.name,
          minPartSize,
          maxPartSize,
          maxPartDuration,
          maxDownloadRetries.toLong(),
          downloadRetryDelay,
          maxConcurrentDownloads.toLong(),
          maxConcurrentUploads.toLong(),
          deleteFilesAfterUpload.asLong,
          json.encodeToString(huyaConfig),
          json.encodeToString(douyinConfig),
        )
      )
    }
  }
}