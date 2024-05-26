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

package github.hua0512.dao

import androidx.room.TypeConverter
import github.hua0512.data.config.*
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadPlatform
import github.hua0512.data.upload.UploadState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converters for Room database
 * @author hua0512
 * @date : 2024/5/15 22:12
 */
class Converters {

  private val json by lazy {
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      encodeDefaults = false
    }
  }

  @TypeConverter
  fun fromVideoFormat(value: String?): VideoFormat? {
    return value?.let { VideoFormat.valueOf(it) }
  }

  @TypeConverter
  fun toVideoFormat(value: VideoFormat?): String? {
    return value?.name
  }

  @TypeConverter
  fun fromHuyaGlobalConfig(value: String?): HuyaConfigGlobal? {
    return value?.let { json.decodeFromString(it) }
  }

  @TypeConverter
  fun toHuyaGlobalConfig(value: HuyaConfigGlobal?): String? {
    return value?.let { json.encodeToString(it) }
  }


  @TypeConverter
  fun fromDouyinGlobalConfig(value: String?): DouyinConfigGlobal? {
    return value?.let { json.decodeFromString(it) }
  }

  @TypeConverter
  fun toDouyinGlobalConfig(value: DouyinConfigGlobal?): String? {
    return value?.let { json.encodeToString(it) }
  }

  @TypeConverter
  fun fromDouyuGlobalConfig(value: String?): DouyuConfigGlobal? {
    return value?.let { json.decodeFromString(it) }
  }

  @TypeConverter
  fun toDouyuGlobalConfig(value: DouyuConfigGlobal?): String? {
    return value?.let { json.encodeToString(it) }
  }

  @TypeConverter
  fun fromTwitchGlobalConfig(value: String?): TwitchConfigGlobal? {
    return value?.let { json.decodeFromString(it) }
  }

  @TypeConverter
  fun toTwitchGlobalConfig(value: TwitchConfigGlobal?): String? {
    return value?.let { json.encodeToString(it) }
  }

  @TypeConverter
  fun fromPandaTvGlobalConfig(value: String?): PandaTvConfigGlobal? {
    return value?.let { json.decodeFromString(it) }
  }

  @TypeConverter
  fun toPandaTvGlobalConfig(value: PandaTvConfigGlobal?): String? {
    return value?.let { json.encodeToString(it) }
  }

  @TypeConverter
  fun fromDownloadConfig(value: String?): DownloadConfig? {
    return value?.let { json.decodeFromString(it) }
  }

  @TypeConverter
  fun toDownloadConfig(value: DownloadConfig?): String? {
    return value?.let { json.encodeToString(it) }
  }

  @TypeConverter
  fun fromStreamingPlatform(value: Int?): StreamingPlatform? {
    return value?.let { StreamingPlatform.fromId(it) }
  }

  @TypeConverter
  fun toStreamingPlatform(value: StreamingPlatform?): Int? {
    return value?.id
  }

  @TypeConverter
  fun fromUploadConfig(value: String?): UploadConfig? {
    return value?.let { json.decodeFromString(it) }
  }

  @TypeConverter
  fun toUploadConfig(value: UploadConfig?): String? {
    return value?.let { json.encodeToString(it) }
  }

  @TypeConverter
  fun fromUploadPlatform(value: Int?): UploadPlatform? {
    return value?.let { UploadPlatform.fromId(it) }
  }

  @TypeConverter
  fun toUploadPlatform(value: UploadPlatform?): Int? {
    return value?.id
  }

  @TypeConverter
  fun fromUploadState(value: Int?): UploadState? {
    return value?.let { UploadState.fromId(it) }
  }

  @TypeConverter
  fun toUploadState(value: UploadState?): Int? {
    return value?.value
  }
}