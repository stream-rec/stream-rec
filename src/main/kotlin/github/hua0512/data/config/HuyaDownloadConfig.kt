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
import github.hua0512.data.dto.HuyaConfigDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("huya")
data class HuyaDownloadConfig(
  override val primaryCdn: String? = null,
  override val danmu: Boolean? = null,
  override val maxBitRate: Int? = null,
  override val outputFolder: String? = null,
  override val outputFileName: String? = null,
  override val outputFileExtension: VideoFormat? = null,
  override val onPartedDownload: List<Action>? = null,
  override val onStreamingFinished: List<Action>? = null,
) : DownloadConfig(), HuyaConfigDTO {

  companion object {
    val default = HuyaDownloadConfig(
      primaryCdn = "AL",
      danmu = false,
      maxBitRate = 10000,
      outputFolder = "",
      outputFileExtension = VideoFormat.flv,
    )
  }

  override val cookies: String? = null
}

