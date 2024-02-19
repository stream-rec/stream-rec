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

package github.hua0512.repo

import github.hua0512.dao.upload.UploadActionDao
import github.hua0512.dao.upload.UploadActionFilesDao
import github.hua0512.dao.upload.UploadDataDao
import github.hua0512.dao.upload.UploadResultDao
import github.hua0512.data.StreamDataId
import github.hua0512.data.UploadActionId
import github.hua0512.data.UploadDataId
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult
import github.hua0512.utils.asLong
import github.hua0512.utils.boolean
import github.hua0512.utils.withIOContext
import kotlinx.serialization.json.Json

/**
 * @author hua0512
 * @date : 2024/2/17 20:20
 */
class UploadActionRepository(
  val json: Json,
  val uploadActionDao: UploadActionDao,
  val uploadDataDao: UploadDataDao,
  val uploadActionFilesDao: UploadActionFilesDao,
  val uploadResultDao: UploadResultDao,
) {


  suspend fun save(uploadAction: UploadAction): UploadActionId {
    val actionId = withIOContext {
      val uploadConfigString = json.encodeToString(UploadConfig.serializer(), uploadAction.uploadConfig)
      uploadActionDao.saveUploadAction(uploadAction.time, uploadConfigString)
    }
    // save upload data
    withIOContext {
      uploadAction.files.forEach {
        val uploadDataId = uploadDataDao.insertUploadData(
          it.streamTitle,
          it.streamer,
          it.streamStartTime,
          it.filePath,
          StreamDataId(it.streamDataId),
          it.status.asLong
        )
        it.id = uploadDataId
        // insert into upload action files
        uploadActionFilesDao.insertUploadActionFiles(actionId, UploadDataId(uploadDataId))
      }
    }
    return actionId
  }

  suspend fun saveResult(uploadResult: UploadResult) {
    return withIOContext {
      uploadResultDao.saveUploadResult(uploadResult.toEntity())
    }
  }

  suspend fun getUploadData(uploadDataId: UploadDataId): UploadData? {
    return withIOContext {
      uploadDataDao.getUploadDataById(uploadDataId)?.let { uploadData ->
        UploadData(
          id = uploadData.id,
          streamTitle = uploadData.streamTitle,
          streamer = uploadData.streamer,
          streamStartTime = uploadData.streamStartTime,
          filePath = uploadData.filePath,
          status = uploadData.status.boolean
        ).also {
          it.streamDataId = it.streamDataId
        }
      }
    }
  }

  suspend fun changeUploadDataStatus(uploadDataId: Long, status: Boolean) {
    return withIOContext {
      uploadDataDao.updateUploadDataStatus(UploadDataId(uploadDataId), status.asLong)
    }
  }


}
