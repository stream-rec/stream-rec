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
import github.hua0512.data.upload.UploadResultWithData
import github.hua0512.data.upload.entity.UploadResultEntity
import kotlinx.coroutines.flow.Flow

/**
 * Upload result related CRUD operations
 *
 * @author hua0512
 * @date : 2024/2/19 10:52
 */
@Dao
interface UploadResultDao : BaseDao<UploadResultEntity> {

  /**
   * Streams all upload results.
   *
   * @return Flow of list of UploadResultEntity
   */
  @Query("SELECT * FROM UploadResult ORDER BY startTime DESC")
  fun streamAllDesc(): Flow<List<UploadResultEntity>>

  /**
   * Streams all failed upload results.
   *
   * @return Flow of list of UploadResultEntity
   */
  @Query("SELECT * FROM UploadResult WHERE isSuccess = 0 ORDER BY startTime DESC")
  fun streamAllFailedDesc(): Flow<List<UploadResultEntity>>

  /**
   * Streams all failed upload results with upload data.
   * @return Flow of list of UploadResultWithData
   */
  @Transaction
  @Query("SELECT * FROM UploadResult WHERE isSuccess = 0 ORDER BY startTime DESC")
  fun streamAllFailedWithUploadDataDesc(): Flow<List<UploadResultWithData>>


  /**
   * Finds all upload results.
   *
   * @return List of UploadResultEntity
   */
  @Query("SELECT * FROM UploadResult ORDER BY startTime DESC")
  suspend fun getAllDesc(): List<UploadResultEntity>


  /**
   * Finds all upload results with upload data.
   *
   * @return List of UploadResultWithData
   */
  @Transaction
  @Query("SELECT * FROM UploadResult ORDER BY startTime DESC")
  suspend fun getAllWithDataDesc(): List<UploadResultWithData>

  /**
   * Finds all upload results paginated.
   *
   * @param page The page number
   * @param pageSize The page size
   * @return List of UploadResultEntity
   */
  @Query("SELECT * FROM UploadResult ORDER BY startTime DESC LIMIT :pageSize OFFSET :page")
  suspend fun getAllPaginated(page: Int, pageSize: Int): List<UploadResultEntity>


  /**
   * Finds all failed upload results.
   *
   * @return List of UploadResultEntity
   */
  @Query("SELECT * FROM UploadResult WHERE isSuccess = 0 ORDER BY startTime DESC")
  suspend fun getAllFailed(): List<UploadResultEntity>

  /**
   * Finds upload results by upload ID.
   *
   * @param uploadId The ID of the upload
   * @return List of UploadResultEntity
   */
  @Query("SELECT * FROM UploadResult WHERE uploadDataId = :uploadId ORDER BY startTime DESC")
  suspend fun findByDataIdDesc(uploadId: Long): List<UploadResultEntity>


  /**
   * Finds upload results by upload ID with upload data.
   * @param uploadId The ID of the upload
   * @return List of UploadResultWithData
   */
  @Transaction
  @Query("SELECT * FROM UploadResult WHERE uploadDataId = :uploadId ORDER BY startTime DESC")
  suspend fun findByDataIdWithDataDesc(uploadId: Long): List<UploadResultWithData>
}