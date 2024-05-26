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

import github.hua0512.data.dto.DownloadConfigDTO
import github.hua0512.data.dto.platform.*
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.platform.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface DownloadConfig : DownloadConfigDTO {

  abstract override val cookies: String?
  abstract override var danmu: Boolean?
  abstract override var maxBitRate: Int?
  abstract override var outputFolder: String?
  abstract override var outputFileName: String?
  abstract override var outputFileFormat: VideoFormat?
  abstract override var onPartedDownload: List<Action>?
  abstract override var onStreamingFinished: List<Action>?

  @Serializable
  @SerialName("template")
  data class DefaultDownloadConfig(
    override val cookies: String? = null,
    override var danmu: Boolean? = null,
    override var maxBitRate: Int? = null,
    override var outputFolder: String? = null,
    override var outputFileName: String? = null,
    override var outputFileFormat: VideoFormat? = null,
    override var onPartedDownload: List<Action>? = emptyList(),
    override var onStreamingFinished: List<Action>? = emptyList(),
  ) : DownloadConfig

  @SerialName("douyin")
  @Serializable
  data class DouyinDownloadConfig(
    override val cookies: String? = null,
    override val quality: DouyinQuality? = null,
    override val sourceFormat: VideoFormat? = null,
  ) : DownloadConfig, DouyinConfigDTO {
    override var danmu: Boolean? = null
    override var maxBitRate: Int? = null
    override var outputFolder: String? = null
    override var outputFileName: String? = null
    override var outputFileFormat: VideoFormat? = null
    override var onPartedDownload: List<Action>? = emptyList()
    override var onStreamingFinished: List<Action>? = emptyList()
  }


  @Serializable
  @SerialName("huya")
  data class HuyaDownloadConfig(
    override val primaryCdn: String? = null,
    override val sourceFormat: VideoFormat? = null,
  ) : DownloadConfig, HuyaConfigDTO {


    override var danmu: Boolean? = null
    override var maxBitRate: Int? = null
    override var outputFolder: String? = null
    override var outputFileName: String? = null
    override var outputFileFormat: VideoFormat? = null
    override var onPartedDownload: List<Action>? = emptyList()
    override var onStreamingFinished: List<Action>? = emptyList()

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


  @Serializable
  @SerialName("douyu")
  data class DouyuDownloadConfig(
    override val cdn: String? = null,
    @Serializable(with = DouyuQualitySerializer::class)
    override val quality: DouyuQuality? = null,
  ) : DownloadConfig, DouyuConfigDTO {

    override var cookies: String? = null
    override var danmu: Boolean? = null
    override var maxBitRate: Int? = null
    override var outputFolder: String? = null
    override var outputFileName: String? = null
    override var outputFileFormat: VideoFormat? = null
    override var onPartedDownload: List<Action>? = emptyList()
    override var onStreamingFinished: List<Action>? = emptyList()
  }

  @Serializable
  @SerialName("twitch")
  data class TwitchDownloadConfig(
    override val authToken: String? = null,
    override val quality: TwitchQuality? = null,
  ) : DownloadConfig, TwitchConfigDTO {
    override var cookies: String? = null
    override var danmu: Boolean? = null
    override var maxBitRate: Int? = null
    override var outputFolder: String? = null
    override var outputFileName: String? = null
    override var outputFileFormat: VideoFormat? = null
    override var onPartedDownload: List<Action>? = emptyList()
    override var onStreamingFinished: List<Action>? = emptyList()
  }

  @Serializable
  @SerialName("pandatv")
  data class PandaTvDownloadConfig(
    override val quality: PandaTvQuality? = null,
    override var cookies: String? = null,
  ) : DownloadConfig, PandaTvConfigDTO {
    override var danmu: Boolean? = null
    override var maxBitRate: Int? = null
    override var outputFolder: String? = null
    override var outputFileName: String? = null
    override var outputFileFormat: VideoFormat? = null
    override var onPartedDownload: List<Action>? = emptyList()
    override var onStreamingFinished: List<Action>? = emptyList()
  }
}