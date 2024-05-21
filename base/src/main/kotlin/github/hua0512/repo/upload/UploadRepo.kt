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

package github.hua0512.repo.upload

import github.hua0512.data.StreamerId
import github.hua0512.data.UploadActionId
import github.hua0512.data.UploadDataId
import github.hua0512.data.upload.UploadAction
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult

interface UploadRepo {

  suspend fun getUploadAction(id: UploadActionId): UploadAction?
  suspend fun saveAction(uploadAction: UploadAction): UploadAction

  suspend fun getAllUploadData(): List<UploadData>
  suspend fun getAllUploadDataPaginated(
    page: Int,
    pageSize: Int,
    status: List<Int>?,
    filter: String?,
    streamers: List<StreamerId>?,
    sortColumn: String?,
    sortOrder: String?,
  ): List<UploadData>

  suspend fun countAllUploadData(status: List<Int>?, filter: String?, streamerId: Collection<StreamerId>?): Long


  suspend fun getUploadData(uploadDataId: UploadDataId): UploadData?
  suspend fun updateUploadData(uploadData: UploadData): Boolean
  suspend fun deleteUploadData(uploadData: UploadData)
  suspend fun getUploadDataResults(uploadDataId: UploadDataId): List<UploadResult>
  suspend fun getAllUploadResults(): List<UploadResult>
  suspend fun deleteUploadResult(result: UploadResult): Boolean
  suspend fun saveResult(uploadResult: UploadResult): UploadResult

}