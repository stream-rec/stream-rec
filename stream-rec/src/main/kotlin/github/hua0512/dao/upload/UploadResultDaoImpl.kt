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

package github.hua0512.dao.upload

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import github.hua0512.StreamRecDatabase
import github.hua0512.dao.BaseDaoImpl
import github.hua0512.data.UploadDataId
import github.hua0512.data.UploadResultId
import github.hua0512.sqldelight.db.UploadResult
import github.hua0512.utils.UploadResultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class UploadResultDaoImpl(override val database: StreamRecDatabase) : BaseDaoImpl, UploadResultDao {
  override fun streamUploadResults(): Flow<List<UploadResult>> {
    return queries.selectAllUploadResult().asFlow().mapToList(Dispatchers.IO)
  }

  override fun streamAllFailedUploadResult(): Flow<List<UploadResultEntity>> {
    return queries.selectAllFailedUploadResult().asFlow().mapToList(Dispatchers.IO)
  }

  override fun findFailedUploadResults(): List<UploadResultEntity> {
    return queries.selectAllFailedUploadResult().executeAsList()
  }

  override fun findUploadResultByUploadId(uploadId: UploadDataId): List<UploadResultEntity> {
    return queries.findResultsByUploadDataId(uploadId.value).executeAsList()
  }

  override fun saveUploadResult(uploadResult: UploadResultEntity): Long {
    queries.insertUploadResult(uploadResult.time, uploadResult.isSuccess, uploadResult.message, uploadResult.filePath, uploadResult.uploadDataId)
    return queries.selectLastInsertedId().executeAsOne()
  }

  override fun deleteUploadResult(uploadResultId: UploadResultId) {
    queries.deleteUploadResult(uploadResultId.value)
  }
}