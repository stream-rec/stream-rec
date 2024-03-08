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

package github.hua0512.utils

import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.data.upload.UploadResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * @author hua0512
 * @date : 2024/2/18 19:59
 */
typealias AppConfigEntity = github.hua0512.sqldelight.db.App_config
typealias StreamerEntity = github.hua0512.sqldelight.db.Streamer
typealias StreamDataEntity = github.hua0512.sqldelight.db.StreamData
typealias UploadResultEntity = github.hua0512.sqldelight.db.UploadResult
typealias UploadActionEntity = github.hua0512.sqldelight.db.UploadAction
typealias UploadDataEntity = github.hua0512.sqldelight.db.UploadData
typealias UploadActionFilesEntity = github.hua0512.sqldelight.db.UploadActionFiles
typealias StatsEntity = github.hua0512.sqldelight.db.Stats


val Long.boolean: Boolean
  get() = this != 0L

val Boolean.asLong: Long
  get() = if (this) 1 else 0

