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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import github.hua0512.dao.BaseDao
import github.hua0512.data.UploadActionId
import github.hua0512.data.upload.UploadActionAndFiles
import github.hua0512.data.upload.entity.UploadActionEntity

/**
 * Interface for managing upload actions.
 * Provides methods for retrieving, saving, and deleting upload actions.
 *
 * @author hua0512
 * @date : 2024/2/19 10:52
 */
@Dao
interface UploadActionDao : BaseDao<UploadActionEntity> {

  /**
   * Retrieves an upload action by its ID.
   *
   * @param id The ID of the upload action
   * @return UploadActionEntity or null if no upload action with the given ID exists
   */
  @Query("SELECT * FROM UploadAction WHERE id = :id")
  suspend fun getById(id: UploadActionId): UploadActionEntity?

  /**
   * Retrieves an upload action by its ID, including the files associated with it.
   * @param id The ID of the upload action
   * @return UploadActionAndFiles or null if no upload action with the given ID exists
   */
  @Transaction
  @Query("SELECT * FROM UploadAction WHERE id = :id")
  suspend fun getByIdWithFiles(id: UploadActionId): UploadActionAndFiles?
}