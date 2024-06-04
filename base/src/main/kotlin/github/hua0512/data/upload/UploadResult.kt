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


import github.hua0512.data.upload.entity.UploadResultEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


/**
 * Upload result data class.
 * @author hua0512
 * @date : 2024/2/9 19:16
 */
@Serializable
data class UploadResult(
  val id: Long = 0,
  val startTime: Long,
  val endTime: Long = 0,
  val isSuccess: Boolean = false,
  val message: String? = "",
  val uploadDataId: Long = 0,
  @Transient
  val uploadData: UploadData? = null,
) {

  val filePath
    get() = uploadData?.filePath

  constructor(entity: UploadResultEntity, uploadData: UploadData? = null) : this(
    entity.id,
    entity.startTime,
    entity.endTime,
    entity.isSuccess,
    entity.message,
    entity.uploadDataId,
    uploadData
  )

  fun toEntity(): UploadResultEntity = UploadResultEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    isSuccess = isSuccess,
    message = message,
    uploadDataId = uploadDataId
  )
}