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

package github.hua0512.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import github.hua0512.StreamRecDatabase
import github.hua0512.utils.AppConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * AppConfigDao implementation
 * @author hua0512
 * @date : 2024/2/19 0:13
 */
class AppConfigDaoImpl(override val database: StreamRecDatabase) : BaseDaoImpl, AppConfigDao {
  override suspend fun getLatestAppConfig(): AppConfigEntity? {
    // always return the 1st streamer's config
    return queries.getAppConfigById(1).executeAsOneOrNull()
  }

  override suspend fun streamLatestAppConfig(): Flow<AppConfigEntity> {
    return queries.getAppConfigById(1).asFlow().mapToOneNotNull(Dispatchers.IO)
  }

  override suspend fun upsert(config: AppConfigEntity) {
    config.apply {
      queries.upsertAppConfig(
        engine = engine,
        danmu = danmu,
        outputFolder = outputFolder,
        outputFileName = outputFileName,
        outputFileFormat = outputFileFormat,
        minPartSize = minPartSize,
        maxPartSize = maxPartSize,
        maxPartDuration = maxPartDuration,
        maxDownloadRetries = maxDownloadRetries,
        downloadRetryDelay = downloadRetryDelay,
        downloadCheckInterval = downloadCheckInterval,
        maxConcurrentDownloads = maxConcurrentDownloads,
        maxConcurrentUploads = maxConcurrentUploads,
        deleteFilesAfterUpload = deleteFilesAfterUpload,
        huyaConfig = huyaConfig,
        douyinConfig = douyinConfig,
        douyuConfig = douyuConfig,
        twitchConfig = twitchConfig,
        pandaliveConfig = pandaliveConfig,
        useBuiltInSegmenter = useBuiltInSegmenter,
        id = id,
      )
    }
  }

}