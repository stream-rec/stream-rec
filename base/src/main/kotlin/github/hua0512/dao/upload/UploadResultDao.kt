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

import github.hua0512.data.UploadDataId
import github.hua0512.data.UploadResultId
import github.hua0512.utils.UploadResultEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing upload results.
 * Provides methods for streaming, finding, saving, and deleting upload results.
 *
 * @author hua0512
 * @date : 2024/2/19 10:52
 */
interface UploadResultDao {

  /**
   * Streams all upload results.
   *
   * @return Flow of list of UploadResultEntity
   */
  fun streamUploadResults(): Flow<List<UploadResultEntity>>

  /**
   * Streams all failed upload results.
   *
   * @return Flow of list of UploadResultEntity
   */
  fun streamAllFailedUploadResult(): Flow<List<UploadResultEntity>>


  /**
   * Finds all upload results.
   *
   * @return List of UploadResultEntity
   */
  fun getAllUploadResults(): List<UploadResultEntity>

  /**
   * Finds all upload results paginated.
   *
   * @param page The page number
   * @param pageSize The page size
   * @return List of UploadResultEntity
   */
  fun getAllUploadResultsPaginated(page: Int, pageSize: Int): List<UploadResultEntity>


  /**
   * Finds all failed upload results.
   *
   * @return List of UploadResultEntity
   */
  fun findFailedUploadResults(): List<UploadResultEntity>

  /**
   * Finds upload results by upload ID.
   *
   * @param uploadId The ID of the upload
   * @return List of UploadResultEntity
   */
  fun findUploadResultByUploadId(uploadId: UploadDataId): List<UploadResultEntity>

  /**
   * Saves an upload result.
   *
   * @param uploadResult The upload result to save
   * @return The ID of the saved upload result
   */
  fun saveUploadResult(uploadResult: UploadResultEntity): Long

  /**
   * Deletes an upload result.
   *
   * @param uploadResultId The ID of the upload result to delete
   */
  fun deleteUploadResult(uploadResultId: UploadResultId)
}