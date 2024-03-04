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

import github.hua0512.data.VideoFormat
import github.hua0512.data.stream.Streamer
import kotlinx.serialization.Serializable

/**
 * Application configuration data class
 * @author hua0512
 * @date : 2024/2/11 13:19
 */
@Serializable
data class AppConfig(
  val engine: String = "ffmpeg",
  val danmu: Boolean = false,
  val outputFolder: String = System.getProperty("user.dir"),
  val outputFileName: String = "{streamer}-{title}-%yyyy-%MM-%dd %HH:%mm:%ss",
  val outputFileFormat: VideoFormat = VideoFormat.flv,
  val minPartSize: Long = 20000000,
  val maxPartSize: Long = 2621440000,
  val maxPartDuration: Long? = null,
  val maxDownloadRetries: Int = 3,
  val downloadRetryDelay: Long = 10,
  val maxConcurrentDownloads: Int = 5,
  val maxConcurrentUploads: Int = 3,
  val deleteFilesAfterUpload: Boolean = true,

  val huyaConfig: HuyaConfigGlobal = HuyaConfigGlobal(),
  val douyinConfig: DouyinConfigGlobal = DouyinConfigGlobal(),

  val streamers: List<Streamer> = emptyList(),
) {
  var id: Long = 1
}