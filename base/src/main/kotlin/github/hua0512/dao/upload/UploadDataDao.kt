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
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import github.hua0512.dao.BaseDao
import github.hua0512.data.UploadDataId
import github.hua0512.data.upload.UploadDataWithStreamAndConfig
import github.hua0512.data.upload.entity.UploadDataEntity
import github.hua0512.data.upload.entity.UploadResultEntity

/**
 * Upload data DAO interface.
 * @author hua0512
 * @date : 2024/2/18 16:06
 */
@Dao
interface UploadDataDao : BaseDao<UploadDataEntity> {

  /**
   * Retrieves all upload data.
   * @return List of UploadDataEntity
   */
  @Query("SELECT * FROM UploadData")
  suspend fun getAll(): List<UploadDataEntity>

  /**
   * Retrieves all upload data with their stream data and upload action.
   * @return List of UploadDataWithStreamAndConfig
   */
  @Transaction
  @Query("SELECT * FROM UploadData")
  suspend fun getAllWithStreamAndAction(): List<UploadDataWithStreamAndConfig>

  /**
   * Retrieves an upload data by its ID.
   * @param id The ID of the upload data
   * @return UploadDataEntity or null if no upload data with the given ID exists
   */
  @Query("SELECT * FROM UploadData WHERE id = :id")
  suspend fun getById(id: UploadDataId): UploadDataEntity?


  /**
   * Retrieves an upload data by its ID with its stream data and upload action.
   * @param id The ID of the upload data
   * @return UploadDataWithStreamAndConfig or null if no upload data with the given ID exists
   */
  @Transaction
  @Query("SELECT * FROM UploadData WHERE id = :id")
  suspend fun getByIdWithStreamAndAction(id: UploadDataId): UploadDataWithStreamAndConfig?

  /**
   * Retrieves all upload data by status.
   * @param status The status of the upload data
   * @return List of UploadDataEntity
   */
  @Query("SELECT * FROM UploadData WHERE status = :status")
  suspend fun getByStatus(status: Long): List<UploadDataEntity>

  /**
   * Retrieves all upload data by filter, paginated and sorted in descending order.
   * It joins the stream data table to get the stream data.
   * @param page The page number
   * @param pageSize The page size
   * @param filter The filter string, where the file path contains the filter string or the title of the stream data contains the filter string
   * @param status The status of the upload data, where the status is in the given status list
   * @param streamerIds The stream IDs, where the stream ID is in the given stream ID list
   * @param allStreamers Whether to show all streamers
   * @param sortColumn The column to sort by in descending order
   * @return List of UploadDataEntity sorted in descending order
   */
  @Transaction
  @RewriteQueriesToDropUnusedColumns
  @Query(
    """
    SELECT * FROM UploadData
    JOIN StreamData ON UploadData.streamDataId = StreamData.id
    WHERE (:allStreamers OR StreamData.streamerId IN (:streamerIds))
    AND (StreamData.title LIKE '%' || :filter || '%' OR UploadData.filePath LIKE '%' || :filter || '%')
    AND (UploadData.status IN (:status))
    ORDER BY CASE
      WHEN :sortColumn = 'id' THEN UploadData.id
      WHEN :sortColumn = 'filePath' THEN UploadData.filePath
      WHEN :sortColumn = 'title' THEN StreamData.title
      ELSE UploadData.id
      END DESC
    LIMIT :pageSize OFFSET :page
    """
  )
  suspend fun getAllFilteredPaginatedDesc(
    page: Int,
    pageSize: Int,
    filter: String,
    status: Collection<Int>?,
    streamerIds: Collection<Long>?,
    allStreamers: Boolean?,
    sortColumn: String,
  ): List<UploadDataWithStreamAndConfig>


  /**
   * Retrieves all upload data by filter, paginated and sorted in ascending order.
   * It joins the stream data table to get the stream data.
   * @param page The page number
   * @param pageSize The page size
   * @param filter The filter string, where the file path contains the filter string or the title of the stream data contains the filter string
   * @param status The status of the upload data, where the status is in the given status list
   * @param streamerIds The stream IDs, where the stream ID is in the given stream ID list
   * @param allStreamers Whether to show all streamers
   * @param sortColumn The column to sort by
   * @return List of UploadDataEntity sorted in ascending order
   */
  @Transaction
  @RewriteQueriesToDropUnusedColumns
  @Query(
    """
    SELECT * FROM UploadData
    JOIN StreamData ON UploadData.streamDataId = StreamData.id
    WHERE (:allStreamers OR StreamData.streamerId IN (:streamerIds))
    AND (StreamData.title LIKE '%' || :filter || '%' OR UploadData.filePath LIKE '%' || :filter || '%')
    AND (UploadData.status IN (:status))
    ORDER BY CASE
      WHEN :sortColumn = 'id' THEN UploadData.id
      WHEN :sortColumn = 'filePath' THEN UploadData.filePath
      WHEN :sortColumn = 'title' THEN StreamData.title
      ELSE UploadData.id
      END ASC
    LIMIT :pageSize OFFSET :page
    """
  )
  suspend fun getAllFilteredPaginatedAsc(
    page: Int,
    pageSize: Int,
    filter: String,
    status: Collection<Int>?,
    streamerIds: Collection<Long>?,
    allStreamers: Boolean?,
    sortColumn: String,
  ): List<UploadDataWithStreamAndConfig>

  /**
   * Counts all upload data by filter.
   * @param status The status of the upload data
   * @param filter The filter string
   * @param streamerIds The stream IDs to filter by
   * @param allStreamers Whether to show all streamers
   * @return The count of upload data
   */
  @Query(
    """
    SELECT COUNT(*) FROM UploadData
    JOIN StreamData ON UploadData.streamDataId = StreamData.id
    WHERE (UploadData.status IN (:status))
    AND (:allStreamers OR StreamData.streamerId IN (:streamerIds))
    AND (StreamData.title LIKE '%' || :filter || '%' OR UploadData.filePath LIKE '%' || :filter || '%')
  """
  )
  suspend fun countAllByFilter(status: Collection<Int>?, filter: String, streamerIds: Collection<Long>, allStreamers: Boolean?): Long


  /**
   * Retrieves all upload data with their upload results by upload data ID.
   * @param id The ID of the upload data
   * @return Map of UploadDataEntity to list of UploadResultEntity
   */
  @Query(
    """
    SELECT * FROM UploadData
    JOIN UploadResult ON UploadData.id = UploadResult.uploadDataId
    WHERE UploadData.id = :id
    ORDER BY UploadResult.startTime DESC
    """
  )
  suspend fun getUploadResults(id: UploadDataId): Map<UploadDataEntity, List<UploadResultEntity>>

}