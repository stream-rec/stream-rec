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
import github.hua0512.data.dto.DownloadConfigDTO
import github.hua0512.data.dto.HuyaConfigDTO
import github.hua0512.data.platform.DouyinQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface DownloadConfig : DownloadConfigDTO {

  abstract override val cookies: String?
  abstract override val danmu: Boolean?
  abstract override val maxBitRate: Int?
  abstract override val outputFolder: String?
  abstract override val outputFileName: String?
  abstract override val outputFileFormat: VideoFormat?
  abstract override val onPartedDownload: List<Action>?
  abstract override val onStreamingFinished: List<Action>?

  @Serializable
  @SerialName("template")
  data class DefaultDownloadConfig(
    override val cookies: String? = null,
    override val danmu: Boolean? = null,
    override val maxBitRate: Int? = null,
    override val outputFolder: String? = null,
    override val outputFileName: String? = null,
    override val outputFileFormat: VideoFormat? = null,
    override val onPartedDownload: List<Action> = emptyList(),
    override val onStreamingFinished: List<Action> = emptyList(),
    override val partedDownloadRetry: Int? = null,
  ) : DownloadConfig

  @SerialName("douyin")
  @Serializable
  data class DouyinDownloadConfig(
    override val cookies: String? = null,
    val quality: DouyinQuality? = null,
  ) : DownloadConfig {
    override var danmu: Boolean? = null
    override var maxBitRate: Int? = null
    override var outputFolder: String? = null
    override var outputFileName: String? = null
    override var outputFileFormat: VideoFormat? = null
    override var onPartedDownload: List<Action> = emptyList()
    override var onStreamingFinished: List<Action> = emptyList()
    override var partedDownloadRetry: Int? = null
  }


  @Serializable
  @SerialName("huya")
  data class HuyaDownloadConfig(
    override val primaryCdn: String? = null,

    ) : DownloadConfig, HuyaConfigDTO {


    override var danmu: Boolean? = null
    override var maxBitRate: Int? = null
    override var outputFolder: String? = null
    override var outputFileName: String? = null
    override var outputFileFormat: VideoFormat? = null
    override var onPartedDownload: List<Action> = emptyList()
    override var onStreamingFinished: List<Action> = emptyList()
    override var partedDownloadRetry: Int? = null

    companion object {
      val default = HuyaDownloadConfig(
        primaryCdn = "AL",
      ).also {
        it.danmu = false
        it.maxBitRate = 10000
        it.outputFolder = ""
        it.outputFileFormat = VideoFormat.flv
        it.onPartedDownload = emptyList()
        it.onStreamingFinished = emptyList()
      }
    }

    override var cookies: String? = null
  }
}