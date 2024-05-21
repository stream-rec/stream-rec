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

package github.hua0512.data.stream

import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.StreamerDTO
import github.hua0512.data.stream.entity.StreamerEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class Streamer(
  val id: Long = 0,
  override val name: String,
  override val url: String,
  override val platform: StreamingPlatform = StreamingPlatform.UNKNOWN,
  override var lastLiveTime: Long = 0,
  override var isLive: Boolean = false,
  override var isActivated: Boolean = true,
  override var avatar: String? = null,
  override var streamTitle: String? = null,
  override val downloadConfig: DownloadConfig? = null,
  override val isTemplate: Boolean = false,
  override val templateId: Long? = 0,
  @Transient
  val templateStreamer: Streamer? = null,
) : StreamerDTO {


  constructor(entity: StreamerEntity, templateStreamer: Streamer? = null) : this(
    id = entity.id,
    name = entity.name,
    url = entity.url,
    platform = entity.platform,
    lastLiveTime = entity.lastLiveTime,
    isLive = entity.isLive,
    isActivated = entity.isActivated,
    avatar = entity.avatar,
    streamTitle = entity.streamTitle,
    downloadConfig = entity.downloadConfig,
    isTemplate = entity.isTemplate,
    templateId = entity.templateId,
    templateStreamer = templateStreamer
  )

  fun toStreamerEntity() = StreamerEntity(
    id = id,
    name = name,
    url = url,
    platform = platform,
    lastLiveTime = lastLiveTime ?: 0,
    isLive = isLive,
    isActivated = isActivated,
    streamTitle = streamTitle,
    avatar = avatar,
    isTemplate = isTemplate,
    templateId = templateId ?: 0,
    downloadConfig = downloadConfig,
    appConfigId = 1
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Streamer

    if (name != other.name) return false
    if (url != other.url) return false
    if (platform != other.platform) return false
    if (isActivated != other.isActivated) return false
    if (downloadConfig != other.downloadConfig) return false
    if (isTemplate != other.isTemplate) return false
    if (id != other.id) return false
    if (templateStreamer != other.templateStreamer) return false
    if (templateId != other.templateId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + platform.hashCode()
    result = 31 * result + isActivated.hashCode()
    result = 31 * result + (downloadConfig?.hashCode() ?: 0)
    result = 31 * result + isTemplate.hashCode()
    result = 31 * result + id.hashCode()
    result = 31 * result + (templateStreamer?.hashCode() ?: 0)
    result = 31 * result + (templateId?.hashCode() ?: 0)
    return result
  }


}
