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

package github.hua0512.plugins.download

import github.hua0512.data.config.Action
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.GlobalPlatformConfig
import github.hua0512.data.dto.platform.*
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.data.stream.StreamingPlatform.*
import github.hua0512.utils.nonEmptyOrNull

/**
 * Streamer config provider
 * @author hua0512
 * @date : 2024/10/11 22:37
 */


fun <T : DownloadConfig> T.fillDownloadConfig(
  platform: StreamingPlatform,
  templateConfig: DownloadConfig?,
  appConfig: AppConfig,
): T {
  val streamerConfig = this

  val newCookies = templateConfig?.cookies.orEmpty()
    .ifEmpty {
      streamerConfig.cookies.orEmpty()
        .ifEmpty { platform.globalConfig(appConfig).cookies }
    }
  val newDanmu = templateConfig?.danmu ?: streamerConfig.danmu ?: appConfig.danmu
  val newMaxBitRate = templateConfig?.maxBitRate ?: streamerConfig.maxBitRate
  val newOutputFolder = templateConfig?.outputFolder?.nonEmptyOrNull() ?: streamerConfig.outputFolder?.nonEmptyOrNull()
  ?: appConfig.outputFolder
  val newOutputFileName =
    templateConfig?.outputFileName?.nonEmptyOrNull() ?: streamerConfig.outputFileName?.nonEmptyOrNull()
    ?: appConfig.outputFileName
  val newOutputFileFormat =
    templateConfig?.outputFileFormat ?: streamerConfig.outputFileFormat ?: appConfig.outputFileFormat
  val onPartedDownload = templateConfig?.onPartedDownload ?: streamerConfig.onPartedDownload
  val onStreamingFinished = templateConfig?.onStreamingFinished ?: streamerConfig.onStreamingFinished

  val platformBasedConfig = when (platform) {
    HUYA -> DownloadConfig.HuyaDownloadConfig(
      primaryCdn = (streamerConfig as HuyaConfigDTO).primaryCdn ?: appConfig.huyaConfig.primaryCdn,
      sourceFormat = streamerConfig.sourceFormat ?: appConfig.huyaConfig.sourceFormat,
    )

    DOUYIN -> DownloadConfig.DouyinDownloadConfig(
      quality = (streamerConfig as DouyinConfigDTO).quality ?: appConfig.douyinConfig.quality,
      sourceFormat = streamerConfig.sourceFormat ?: appConfig.douyinConfig.sourceFormat,
    )

    DOUYU -> DownloadConfig.DouyuDownloadConfig(
      cdn = (streamerConfig as DouyuConfigDTO).cdn ?: appConfig.douyuConfig.cdn,
      quality = streamerConfig.quality ?: appConfig.douyuConfig.quality,
    )

    TWITCH -> DownloadConfig.TwitchDownloadConfig(
      authToken = (streamerConfig as TwitchConfigDTO).authToken ?: appConfig.twitchConfig.authToken,
      quality = streamerConfig.quality ?: appConfig.twitchConfig.quality,
    )

    PANDATV -> DownloadConfig.PandaTvDownloadConfig(
      quality = (streamerConfig as PandaTvConfigDTO).quality ?: appConfig.pandaTvConfig.quality,
    )

    WEIBO -> DownloadConfig.WeiboDownloadConfig(
      sourceFormat = (streamerConfig as DownloadConfig.WeiboDownloadConfig).sourceFormat
        ?: appConfig.weiboConfig.sourceFormat,
    )

    UNKNOWN -> throw UnsupportedOperationException("Platform not supported")
  } as T

  return platformBasedConfig.apply {
    applyCommonFields(
      newCookies,
      newDanmu,
      newMaxBitRate,
      newOutputFolder,
      newOutputFileName,
      newOutputFileFormat,
      onPartedDownload,
      onStreamingFinished
    )
  }
}

private fun DownloadConfig.applyCommonFields(
  newCookies: String?,
  newDanmu: Boolean?,
  newMaxBitRate: Int?,
  newOutputFolder: String?,
  newOutputFileName: String?,
  newOutputFileFormat: VideoFormat?,
  onPartedDownload: List<Action>?,
  onStreamingFinished: List<Action>?,
) {
  this.cookies = newCookies
  this.danmu = newDanmu
  this.maxBitRate = newMaxBitRate
  this.outputFolder = newOutputFolder
  this.outputFileName = newOutputFileName
  this.outputFileFormat = newOutputFileFormat
  this.onPartedDownload = onPartedDownload
  this.onStreamingFinished = onStreamingFinished
}

/**
 * Returns the global platform config for the platform
 *
 * @param config [AppConfig] global app config
 * @return [GlobalPlatformConfig] streaming global platform config
 */
fun StreamingPlatform.globalConfig(config: AppConfig): GlobalPlatformConfig = when (this) {
  HUYA -> config.huyaConfig
  DOUYIN -> config.douyinConfig
  DOUYU -> config.douyuConfig
  TWITCH -> config.twitchConfig
  PANDATV -> config.pandaTvConfig
  WEIBO -> config.weiboConfig
  else -> throw UnsupportedOperationException("Platform not supported")
}