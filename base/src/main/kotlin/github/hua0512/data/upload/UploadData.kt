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
import github.hua0512.data.upload.entity.UploadDataEntity
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class UploadData(
  val id: Long = 0,
  val filePath: String,
  @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
  val status: UploadState = UploadState.NOT_STARTED,
  @Transient
  val streamData: StreamData? = null,
  @Transient
  val uploadAction: UploadAction? = null,
) {

  constructor(entity: UploadDataEntity, streamData: StreamData? = null, uploadAction: UploadAction? = null) : this(
    id = entity.id,
    filePath = entity.filePath,
    status = entity.status,
    streamData = streamData,
    uploadAction = uploadAction
  )

  var streamDataId: Long = 0
    get() = streamData?.id ?: field

  var streamerId: Long = 0
    get() = streamData?.streamerId ?: field

  var streamTitle = ""
    get() = streamData?.title ?: field


  var streamer = ""
    get() = streamData?.streamerName ?: field

  var streamStartTime: Long = 0L
    get() = streamData?.dateStart ?: field

  var uploadActionId: Long = 0
    get() = uploadAction?.id ?: field

  var uploadPlatform = UploadPlatform.NONE
    get() = uploadAction?.uploadConfig?.platform ?: field

  var uploadConfig: UploadConfig = UploadConfig.NoopConfig
    get() = uploadAction?.uploadConfig ?: field


  fun toEntity(): UploadDataEntity = UploadDataEntity(
    id = id,
    filePath = filePath,
    status = status,
    streamDataId = streamDataId,
    uploadActionId = uploadActionId
  )
}
