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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * @author hua0512
 * @date : 2024/2/18 23:55
 */
class LocalDataSourceImpl(private val dao: AppConfigDao, private val json: Json) : LocalDataSource {
  override suspend fun streamAppConfig(): Flow<AppConfig> {
    return dao.streamLatestAppConfig()?.map {
      it.toAppConfig()
    } ?: emptyFlow()
  }

  private fun AppConfigEntity.toAppConfig(): AppConfig {
    return AppConfig(
      engine,
      danmu.boolean,
      outputFolder?.nonEmptyOrNull() ?: System.getProperty("user.dir"),
      outputFileName.nonEmptyOrNull() ?: "{streamer}-{title}-%yyyy-%MM-%dd %HH:%mm:%ss",
      VideoFormat.format(outputFileFormat) ?: VideoFormat.flv,
      minPartSize,
      maxPartSize,
      maxPartDuration,
      maxDownloadRetries.toInt(),
      downloadRetryDelay,
      maxConcurrentDownloads.toInt(),
      maxConcurrentUploads.toInt(),
      deleteFilesAfterUpload.boolean,
      huyaConfig?.run {
        json.decodeFromString<HuyaConfigGlobal>(this)
      } ?: HuyaConfigGlobal(),
      douyinConfig?.run {
        json.decodeFromString<DouyinConfigGlobal>(this)
      } ?: DouyinConfigGlobal(),
    ).apply {
      this.id = this@toAppConfig.id
    }
  }

  override suspend fun getAppConfig(): AppConfig {
    return withIOContext {
      dao.getLatestAppConfig()?.toAppConfig() ?: AppConfig()
    }
  }

  override fun getPath(): String {
    return LocalDataSource.getDefaultPath()
  }

  override suspend fun saveAppConfig(appConfig: AppConfig) {
    return appConfig.run {
      dao.upsert(
        engine,
        danmu,
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
        deleteFilesAfterUpload,
        json.encodeToString(huyaConfig),
        json.encodeToString(douyinConfig),
        1,
      )
    }
  }
}