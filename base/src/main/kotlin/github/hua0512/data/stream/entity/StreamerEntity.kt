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
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.data.stream.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import github.hua0512.data.config.AppConfigEntity
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.engine.DownloadEngines
import github.hua0512.data.config.engine.EngineConfig
import github.hua0512.data.dto.StreamerDTO
import github.hua0512.data.stream.StreamerState
import github.hua0512.data.stream.StreamingPlatform

/**
 * Streamer entity
 */
@Entity(
  tableName = "streamer", foreignKeys = [
    ForeignKey(
      entity = AppConfigEntity::class,
      parentColumns = ["id"],
      childColumns = ["app_config_id"],
      onDelete = ForeignKey.CASCADE
    ),
  ]
)
data class StreamerEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long,
  @ColumnInfo(name = "name")
  override val name: String,
  @ColumnInfo(name = "url", index = true)
  override val url: String,
  @ColumnInfo(name = "platform")
  override val platform: StreamingPlatform = StreamingPlatform.UNKNOWN,
  @ColumnInfo(name = "last_stream")
  override val lastLiveTime: Long = 0,
  @ColumnInfo(name = "state", defaultValue = "0")
  override val state: StreamerState = StreamerState.NOT_LIVE,
  @ColumnInfo(name = "avatar")
  override val avatar: String? = null,
  @ColumnInfo(name = "description")
  override val streamTitle: String? = null,
  @ColumnInfo(name = "start_time")
  override val startTime: String? = null,
  @ColumnInfo(name = "end_time")
  override val endTime: String? = null,
  @ColumnInfo(name = "download_config")
  override val downloadConfig: DownloadConfig? = null,
  @ColumnInfo(name = "is_template")
  override val isTemplate: Boolean = false,
  @ColumnInfo(name = "template_id", defaultValue = "0")
  override val templateId: Long = 0,
  @ColumnInfo(name = "app_config_id", index = true, defaultValue = "0")
  val appConfigId: Long = 0,
  @ColumnInfo(name = "engine")
  override val engine: DownloadEngines? = null,
  @ColumnInfo(name = "engine_config")
  override val engineConfig: EngineConfig? = null,
) : StreamerDTO