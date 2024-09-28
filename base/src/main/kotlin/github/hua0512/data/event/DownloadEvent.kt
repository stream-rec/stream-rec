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

package github.hua0512.data.event

import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.StreamingPlatform
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * @author hua0512
 * @date : 2024/3/17 21:23
 */
@Serializable
sealed class DownloadEvent : Event {

  abstract val filePath: String
  abstract val url: String
  abstract val platform: StreamingPlatform

  data class DownloadStart(
    override val filePath: String,
    override val url: String,
    override val platform: StreamingPlatform,
  ) : DownloadEvent()

  data class DownloadSuccess(
    override val filePath: String,
    override val url: String,
    override val platform: StreamingPlatform,
    val data: StreamData,
    val time: Instant,
  ) : DownloadEvent()

  @Serializable
  data class DownloadStateUpdate(
    override val filePath: String,
    override val url: String,
    override val platform: StreamingPlatform,
    val duration: Long,
    val bitrate: Double,
    val fileSize: Long,
    val streamerId: Long,
  ) : DownloadEvent()


  data class DownloadError(
    override val filePath: String,
    override val url: String,
    override val platform: StreamingPlatform,
    val error: Throwable,
  ) : DownloadEvent()
}