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
import github.hua0512.utils.StreamerEntity
import github.hua0512.utils.asLong
import github.hua0512.utils.boolean
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Serializable
data class Streamer(
  override val name: String,
  override val url: String,
  override val platform: StreamingPlatform = StreamingPlatform.UNKNOWN,
  override var lastLiveTime: Long? = 0,
  override var isLive: Boolean = false,
  override var isActivated: Boolean = true,
  override var avatar: String? = null,
  override var streamTitle: String? = null,
  override val downloadConfig: DownloadConfig? = null,
  override val isTemplate: Boolean = false,
) : StreamerDTO {

  var id: Long = -1

  @Transient
  override var templateStreamer: Streamer? = null

  override var templateId: Long? = -1

  constructor(entity: StreamerEntity, json: Json) : this(
    name = entity.name,
    url = entity.url,
    platform = StreamingPlatform.fromId(entity.platform.toInt()) ?: StreamingPlatform.UNKNOWN,
    lastLiveTime = entity.last_stream,
    isLive = entity.is_live.boolean,
    isActivated = entity.is_active.boolean,
    avatar = entity.avatar,
    streamTitle = entity.description,
    downloadConfig = if (entity.download_config != null) {
      json.decodeFromString<DownloadConfig>(entity.download_config)
    } else null,
    isTemplate = entity.is_template.boolean
  ) {
    id = entity.streamer_id
    templateId = entity.template_id
  }

  fun toStreamerEntity(json: Json) = StreamerEntity(
    streamer_id = id,
    name = name,
    url = url,
    platform = platform.id.toLong(),
    last_stream = lastLiveTime ?: 0,
    is_live = isLive.asLong,
    is_active = isActivated.asLong,
    description = streamTitle,
    avatar = avatar,
    is_template = isTemplate.asLong,
    template_id = templateId,
    download_config = downloadConfig?.let { json.encodeToString<DownloadConfig>(it) },
    app_config_id = 1
  )
}
