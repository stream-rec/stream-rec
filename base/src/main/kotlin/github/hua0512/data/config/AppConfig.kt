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

package github.hua0512.data.config

import github.hua0512.data.media.VideoFormat
import github.hua0512.utils.nonEmptyOrNull
import kotlinx.serialization.Serializable

/**
 * Application configuration data class
 * @author hua0512
 * @date : 2024/2/11 13:19
 */
@Serializable
data class AppConfig(
  val id: Int = 0,
  val engine: String = CONFIG_DEFAULT_ENGINE,
  val danmu: Boolean = CONFIG_DEFAULT_DANMU,
  val outputFolder: String = System.getenv("DOWNLOAD_PATH") ?: System.getProperty("user.dir"),
  val outputFileName: String = CONFIG_DEFAULT_OUTPUT_FILE_NAME,
  val outputFileFormat: VideoFormat = VideoFormat.flv,
  val minPartSize: Long = CONFIG_DEFAULT_MIN_PART_SIZE,
  val maxPartSize: Long = CONFIG_DEFAULT_MAX_PART_SIZE,
  val maxPartDuration: Long? = null,
  val maxDownloadRetries: Int = 3,
  val downloadRetryDelay: Long = 10,
  val downloadCheckInterval: Long = 60,
  val maxConcurrentDownloads: Int = 5,
  val maxConcurrentUploads: Int = 3,
  val deleteFilesAfterUpload: Boolean = false,
  val useBuiltInSegmenter: Boolean = false,
  val exitDownloadOnError: Boolean = false,
  val huyaConfig: HuyaConfigGlobal = HuyaConfigGlobal(),
  val douyinConfig: DouyinConfigGlobal = DouyinConfigGlobal(),
  val douyuConfig: DouyuConfigGlobal = DouyuConfigGlobal(),
  val twitchConfig: TwitchConfigGlobal = TwitchConfigGlobal(),
  val pandaTvConfig: PandaTvConfigGlobal = PandaTvConfigGlobal(),
) {

  constructor(entity: AppConfigEntity) : this(
    entity.id,
    entity.engine,
    entity.danmu,
    entity.outputFolder.nonEmptyOrNull() ?: System.getenv("DOWNLOAD_PATH") ?: System.getProperty("user.dir"),
    entity.outputFileName.nonEmptyOrNull() ?: CONFIG_DEFAULT_OUTPUT_FILE_NAME,
    entity.outputFileFormat,
    entity.minPartSize,
    entity.maxPartSize,
    entity.maxPartDuration,
    entity.maxDownloadRetries,
    entity.downloadRetryDelay,
    entity.downloadCheckInterval,
    entity.maxConcurrentDownloads,
    entity.maxConcurrentUploads,
    entity.deleteFilesAfterUpload,
    entity.useBuiltInSegmenter,
    entity.exitDownloadOnError,
    entity.huyaConfig,
    entity.douyinConfig,
    entity.douyuConfig,
    entity.twitchConfig,
    entity.pandaTvConfig,
  )


  fun toEntity(): AppConfigEntity = AppConfigEntity(
    id,
    engine,
    danmu,
    outputFolder,
    outputFileName,
    outputFileFormat,
    minPartSize,
    maxPartSize,
    maxPartDuration,
    maxDownloadRetries,
    downloadRetryDelay,
    downloadCheckInterval,
    maxConcurrentDownloads,
    maxConcurrentUploads,
    deleteFilesAfterUpload,
    useBuiltInSegmenter,
    exitDownloadOnError,
    huyaConfig,
    douyinConfig,
    douyuConfig,
    twitchConfig,
    pandaTvConfig,
  )
}