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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import github.hua0512.data.media.VideoFormat

/**
 * Application configuration entity
 * @author hua0512
 * @date : 2024/5/15 22:06
 */
@Entity(tableName = "app_config")
data class AppConfigEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Int = 0,
  @ColumnInfo(name = "engine", defaultValue = "ffmpeg")
  val engine: String = "ffmpeg",
  @ColumnInfo(name = "danmu", defaultValue = "0")
  val danmu: Boolean = false,
  @ColumnInfo(name = "outputFolder")
  val outputFolder: String = System.getenv("DOWNLOAD_PATH") ?: System.getProperty("user.dir"),
  @ColumnInfo(name = "outputFileName")
  val outputFileName: String = "{stream}-{title}-%Y-%m-%d %H:%M:%S",
  @ColumnInfo(name = "outputFileFormat", defaultValue = "flv")
  val outputFileFormat: VideoFormat = VideoFormat.flv,
  @ColumnInfo(name = "minPartSize")
  val minPartSize: Long = 20971520,
  @ColumnInfo(name = "maxPartSize")
  val maxPartSize: Long = 2684354560,
  @ColumnInfo(name = "maxPartDuration")
  val maxPartDuration: Long? = null,
  @ColumnInfo(name = "maxDownloadRetries")
  val maxDownloadRetries: Int = 3,
  @ColumnInfo(name = "downloadRetryDelay")
  val downloadRetryDelay: Long = 10,
  @ColumnInfo(name = "downloadCheckInterval")
  val downloadCheckInterval: Long = 60,
  @ColumnInfo(name = "maxConcurrentDownloads")
  val maxConcurrentDownloads: Int = 5,
  @ColumnInfo(name = "maxConcurrentUploads")
  val maxConcurrentUploads: Int = 3,
  @ColumnInfo(name = "deleteFilesAfterUpload")
  val deleteFilesAfterUpload: Boolean = false,
  @ColumnInfo(name = "useBuiltInSegmenter")
  val useBuiltInSegmenter: Boolean = false,
  @ColumnInfo(name = "huyaConfig")
  val huyaConfig: HuyaConfigGlobal = HuyaConfigGlobal(),
  @ColumnInfo(name = "douyinConfig")
  val douyinConfig: DouyinConfigGlobal = DouyinConfigGlobal(),
  @ColumnInfo(name = "douyuConfig")
  val douyuConfig: DouyuConfigGlobal = DouyuConfigGlobal(),
  @ColumnInfo(name = "twitchConfig")
  val twitchConfig: TwitchConfigGlobal = TwitchConfigGlobal(),
  @ColumnInfo(name = "pandaliveConfig")
  val pandaliveConfig: PandaliveConfigGlobal = PandaliveConfigGlobal(),
)