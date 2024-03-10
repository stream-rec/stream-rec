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

package github.hua0512.data.upload

import github.hua0512.data.stream.StreamData
import github.hua0512.utils.UploadDataEntity
import github.hua0512.utils.asLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class UploadData(
  var id: Long = 0,
  val filePath: String,
  var status: Boolean = false,
) {
  var streamDataId: Long = -1
    get() = if (isStreamDataInitialized()) streamData.id else field

  var streamerId: Long = -1
    get() = if (isStreamDataInitialized()) streamData.streamerId else field

  @Transient
  lateinit var streamData: StreamData

  var streamTitle = ""
    get() = streamData.title

  var streamer = ""
    get() = streamData.streamerName

  var streamStartTime: Long = 0
    get() = streamData.dateStart!!

  var uploadActionId: Long = -1
    get() = if (isUploadActionInitialized()) uploadAction.id else field

  var uploadPlatform = ""
    get() = if (isUploadActionInitialized()) uploadAction.uploadConfig.platform.toString() else field

  var uploadTime: Long = 0
    get() = if (isUploadActionInitialized()) uploadAction.time else field

  var uploadConfig: UploadConfig = UploadConfig.NoopConfig
    get() = if (isUploadActionInitialized()) uploadAction.uploadConfig else field

  @Transient
  lateinit var uploadAction: UploadAction

  fun isStreamDataInitialized() = ::streamData.isInitialized

  fun isUploadActionInitialized() = ::uploadAction.isInitialized

  fun toEntity(): UploadDataEntity {
    return UploadDataEntity(
      id = id,
      filePath = filePath,
      status = status.asLong,
      streamDataId = streamDataId
    )
  }
}
