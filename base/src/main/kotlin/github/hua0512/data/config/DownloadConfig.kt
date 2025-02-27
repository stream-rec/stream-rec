/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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
import github.hua0512.data.platform.DouyinQuality
import github.hua0512.data.platform.DouyuQuality
import github.hua0512.data.platform.DouyuQualitySerializer
import github.hua0512.data.platform.HlsQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DownloadConfig : DownloadConfigDTO {

  //  abstract override val isInherited: Boolean
  override var cookies: String? = null
  override var danmu: Boolean? = null
  override var maxBitRate: Int? = null
  override var outputFolder: String? = null
  override var outputFileName: String? = null
  override var outputFileFormat: VideoFormat? = null
  override var onPartedDownload: List<Action>? = null
  override var onStreamingFinished: List<Action>? = null


  private fun DownloadConfig.checkBasicOther(
    other: DownloadConfig,
  ): Boolean {
    if (cookies != other.cookies) return false
    if (danmu != other.danmu) return false
    if (maxBitRate != other.maxBitRate) return false
    if (outputFolder != other.outputFolder) return false
    if (outputFileName != other.outputFileName) return false
    if (outputFileFormat != other.outputFileFormat) return false
    if (onPartedDownload != other.onPartedDownload) return false
    if (onStreamingFinished != other.onStreamingFinished) return false
    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DownloadConfig

    return this.checkBasicOther(other)
  }

  override fun hashCode(): Int {
    var result = (cookies?.hashCode() ?: 0)
    result = 31 * result + (danmu?.hashCode() ?: 0)
    result = 31 * result + (maxBitRate ?: 0)
    result = 31 * result + (outputFolder?.hashCode() ?: 0)
    result = 31 * result + (outputFileName?.hashCode() ?: 0)
    result = 31 * result + (outputFileFormat?.hashCode() ?: 0)
    result = 31 * result + (onPartedDownload?.hashCode() ?: 0)
    result = 31 * result + (onStreamingFinished?.hashCode() ?: 0)
    return result
  }

  @Serializable
  @SerialName("template")
  data class DefaultDownloadConfig(
    val isInherited: Boolean? = true,
  ) : DownloadConfig()


  @SerialName("douyin")
  @Serializable
  data class DouyinDownloadConfig(
    override val quality: DouyinQuality? = null,
    override val sourceFormat: VideoFormat? = null,
  ) : DownloadConfig(), DouyinConfigDTO {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as DouyinDownloadConfig

      if (quality != other.quality) return false
      if (sourceFormat != other.sourceFormat) return false
      return super.equals(other)
    }
  }


  @Serializable
  @SerialName("huya")
  data class HuyaDownloadConfig(
    override val primaryCdn: String? = null,
    override val sourceFormat: VideoFormat? = null,
  ) : DownloadConfig(), HuyaConfigDTO {

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

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as HuyaDownloadConfig

      if (primaryCdn != other.primaryCdn) return false
      if (sourceFormat != other.sourceFormat) return false
      return super.equals(other)
    }

    override fun hashCode(): Int {
      var result = primaryCdn?.hashCode() ?: 0
      result = 31 * result + (sourceFormat?.hashCode() ?: 0)
      return result
    }
  }


  @Serializable
  @SerialName("douyu")
  data class DouyuDownloadConfig(
    override val cdn: String? = null,
    @Serializable(with = DouyuQualitySerializer::class)
    override val quality: DouyuQuality? = null,
  ) : DownloadConfig(), DouyuConfigDTO {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as DouyuDownloadConfig

      if (cdn != other.cdn) return false
      if (quality != other.quality) return false

      return super.equals(other)
    }

    override fun hashCode(): Int {
      var result = cdn?.hashCode() ?: 0
      result = 31 * result + (quality?.hashCode() ?: 0)
      return result
    }

  }

  @Serializable
  @SerialName("twitch")
  data class TwitchDownloadConfig(
    override val authToken: String? = null,
    override val quality: HlsQuality? = null,
  ) : DownloadConfig(), TwitchConfigDTO {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as TwitchDownloadConfig

      if (authToken != other.authToken) return false
      return super.equals(other)
    }

    override fun hashCode(): Int {
      var result = authToken?.hashCode() ?: 0
      result = 31 * result + (quality?.hashCode() ?: 0)
      return result
    }

  }

  @Serializable
  @SerialName("pandatv")
  data class PandaTvDownloadConfig(
    override val quality: HlsQuality? = null,
  ) : DownloadConfig(), PandaTvConfigDTO {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as PandaTvDownloadConfig

      if (quality != other.quality) return false

      return super.equals(other)
    }

    override fun hashCode(): Int {
      return quality?.hashCode() ?: 0
    }
  }

  @Serializable
  @SerialName("weibo")
  data class WeiboDownloadConfig(
    override val sourceFormat: VideoFormat? = null,
  ) : DownloadConfig(), WeiboConfigDTO {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as WeiboDownloadConfig

      if (sourceFormat != other.sourceFormat) return false

      return super.equals(other)
    }

    override fun hashCode(): Int {
      return sourceFormat?.hashCode() ?: 0
    }

  }
}