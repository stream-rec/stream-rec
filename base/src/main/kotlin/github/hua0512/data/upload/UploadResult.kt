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

import github.hua0512.utils.UploadResultEntity
import github.hua0512.utils.asLong
import github.hua0512.utils.boolean


/**
 * @author hua0512
 * @date : 2024/2/9 19:16
 */
data class UploadResult(
  var id: Long = 0,
  val time: Long,
  val isSuccess: Boolean = false,
  val message: String = "",
) {

  var uploadDataId: Long = -1
    get() {
      if (!isUploadDataInitialized()) {
        return field
      }
      return uploadData.id
    }

  @Transient
  lateinit var uploadData: UploadData

  val filePath
    get() = uploadData.filePath

  constructor(entity: UploadResultEntity) : this(
    entity.id,
    entity.time,
    entity.isSuccess.boolean,
    entity.message.toString(),
  ) {
    uploadDataId = entity.uploadDataId
  }


  fun isUploadDataInitialized() = !::uploadData.isInitialized

  fun toEntity(): UploadResultEntity {
    return UploadResultEntity(
      id = id,
      time = time,
      isSuccess = isSuccess.asLong,
      message = message,
      uploadDataId = uploadDataId
    )
  }
}