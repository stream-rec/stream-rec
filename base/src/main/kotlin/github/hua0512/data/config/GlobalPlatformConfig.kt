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

import github.hua0512.data.dto.platform.*
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.platform.DouyinQuality
import github.hua0512.data.platform.DouyuQuality
import github.hua0512.data.platform.DouyuQualitySerializer
import github.hua0512.data.platform.HlsQuality
import kotlinx.serialization.Serializable

/**
 * Global configuration classes for different platforms
 * @author hua0512
 * @date : 2024/5/4 14:45
 */
@Serializable
sealed interface GlobalPlatformConfig {
  /**
   * Delay in seconds before fetching next streamer
   */
  val fetchDelay: Long?

  /**
   * Delay in seconds before retrying a parted download
   */
  val partedDownloadRetry: Int?

  /**
   * Cookies for the platform
   */
  val cookies: String?

  /**
   * Download check interval in seconds
   */
  val downloadCheckInterval: Long?

  @Serializable
  data class DouyinConfigGlobal(
    override val cookies: String? = null,
    override val quality: DouyinQuality = DouyinQuality.origin,
    override val partedDownloadRetry: Int? = 0,
    override val sourceFormat: VideoFormat? = VideoFormat.flv,
    override val fetchDelay: Long? = 0,
    override val downloadCheckInterval: Long? = null,
  ) : GlobalPlatformConfig, DouyinConfigDTO

  @Serializable
  data class DouyuConfigGlobal(
    override val cdn: String = "tct-h5",
    @Serializable(with = DouyuQualitySerializer::class)
    override val quality: DouyuQuality = DouyuQuality.ORIGIN,
    override val cookies: String? = null,
    override val partedDownloadRetry: Int? = 10,
    override val fetchDelay: Long? = 0,
    override val downloadCheckInterval: Long? = null,
  ) : GlobalPlatformConfig, DouyuConfigDTO


  @Serializable
  data class HuyaConfigGlobal(
    override val primaryCdn: String = "AL",
    override val maxBitRate: Int? = 10000,
    override val cookies: String? = null,
    override val partedDownloadRetry: Int? = 10,
    override val sourceFormat: VideoFormat? = VideoFormat.flv,
    override val fetchDelay: Long? = 0,
    val forceOrigin: Boolean = false,
    val useMobileApi: Boolean = false,
    override val downloadCheckInterval: Long? = null,
  ) : GlobalPlatformConfig, HuyaConfigDTO

  @Serializable
  data class TwitchConfigGlobal(
    override val authToken: String = "",
    override val quality: HlsQuality = HlsQuality.Source,
    override val partedDownloadRetry: Int? = 10,
    override val cookies: String? = null,
    override val fetchDelay: Long? = 30,
    val skipAds: Boolean = false,
    val twitchProxyPlaylist: String? = null,
    val twitchProxyPlaylistExclude: String? = null,
    val twitchProxyPlaylistFallback: Boolean = true,
    override val downloadCheckInterval: Long? = null,
  ) : GlobalPlatformConfig, TwitchConfigDTO

  @Serializable
  data class PandaTvConfigGlobal(
    override val partedDownloadRetry: Int? = 10,
    override val cookies: String? = null,
    override val quality: HlsQuality = HlsQuality.Source,
    override val fetchDelay: Long? = 30,
    override val downloadCheckInterval: Long? = null,
  ) : GlobalPlatformConfig, PandaTvConfigDTO


  @Serializable
  data class WeiboConfigGlobal(
    override val cookies: String? = null,
    override val fetchDelay: Long? = 0,
    override val partedDownloadRetry: Int? = 0,
    override val sourceFormat: VideoFormat? = VideoFormat.flv,
    override val downloadCheckInterval: Long? = null,
  ) : GlobalPlatformConfig, WeiboConfigDTO
}
