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

package github.hua0512.dao.stream

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import github.hua0512.dao.BaseDao
import github.hua0512.data.StreamerId
import github.hua0512.data.stream.StreamDataWithStreamer
import github.hua0512.data.stream.entity.StreamDataEntity
import github.hua0512.data.stream.entity.StreamerEntity

/**
 * Dao for stream data entity
 * @author hua0512
 * @date : 2024/2/19 10:10
 */
@Dao
interface StreamDataDao : BaseDao<StreamDataEntity> {


  /**
   * Get stream data by id
   * @param id stream data id
   * @return stream data entity or null
   */
  @Query("SELECT * FROM StreamData WHERE id = :id")
  suspend fun getById(id: Long): StreamDataEntity?


  /**
   * Get stream data by id with stream
   * @param id stream data id
   * @return stream data entity with stream
   */
  @Transaction
  @Query("SELECT * FROM StreamData WHERE id = :id")
  suspend fun getWithStreamerById(id: Long): StreamDataWithStreamer?

  /**
   * Get all stream data
   * @return list of stream data entities
   */
  @Query("SELECT * FROM StreamData")
  suspend fun getAll(): List<StreamDataEntity>

  /**
   * Get all stream data with stream.
   * Uses multimap feature of Room to return a map of stream and stream data entities
   * @return map of stream and stream data entities
   */
  @Query("SELECT * FROM streamer JOIN StreamData ON Streamer.id = StreamData.streamerId")
  suspend fun getAllWithStreamer(): Map<StreamerEntity, List<StreamDataEntity>>

  /**
   * Get all stream data paged that match the filter
   * @param page page number
   * @param pageSize page size
   * @param filter filter string
   * @param streamerIds stream ids
   * @param allStreamers whether to show all streamers
   * @param dateStart start date
   * @param dateEnd end date
   * @param sortColumn sort column
   * @return list of filtered stream data entities
   *
   */
  @Transaction
  @Query(
    """
     SELECT * FROM StreamData
     WHERE (:allStreamers OR streamerId IN (:streamerIds))
     AND (:dateStart IS NULL OR dateStart >= :dateStart )
     AND (:dateEnd IS NULL OR dateEnd <= :dateEnd )
     AND (:filter IS NULL OR title LIKE '%' || :filter || '%' OR outputFilePath LIKE '%' || :filter || '%' OR danmuFilePath LIKE '%' || :filter || '%')
     ORDER BY CASE
         WHEN :sortColumn = 'id' THEN id
         WHEN :sortColumn = 'title' THEN title
         WHEN :sortColumn = 'dateStart' THEN dateStart
         WHEN :sortColumn = 'dateEnd' THEN dateEnd
         WHEN :sortColumn = 'outputFileSize' THEN outputFileSize
         ELSE dateStart
         END ASC
     LIMIT :pageSize OFFSET :page
    """
  )
  suspend fun getAllPagedAsc(
    page: Int,
    pageSize: Int,
    filter: String?,
    streamerIds: Collection<StreamerId>?,
    allStreamers: Boolean?,
    dateStart: Long?,
    dateEnd: Long?,
    sortColumn: String,
  ): List<StreamDataWithStreamer>


  /**
   * Get all stream data paged that match the filter
   * @param page page number
   * @param pageSize page size
   * @param filter filter string
   * @param streamerIds stream ids
   * @param allStreamers whether to show all streamers
   * @param dateStart start date
   * @param dateEnd end date
   * @param sortColumn sort column
   * @return list of filtered stream data entities
   *
   */
  @Transaction
  @Query(
    """
     SELECT * FROM StreamData
     WHERE (:allStreamers OR streamerId IN (:streamerIds))
     AND (:dateStart IS NULL OR dateStart >= :dateStart )
     AND (:dateEnd IS NULL OR dateEnd <= :dateEnd )
     AND (:filter IS NULL OR title LIKE '%' || :filter || '%' OR outputFilePath LIKE '%' || :filter || '%' OR danmuFilePath LIKE '%' || :filter || '%')
     ORDER BY CASE
         WHEN :sortColumn = 'id' THEN id
         WHEN :sortColumn = 'title' THEN title
         WHEN :sortColumn = 'dateStart' THEN dateStart
         WHEN :sortColumn = 'dateEnd' THEN dateEnd
         WHEN :sortColumn = 'outputFileSize' THEN outputFileSize
         ELSE dateStart
         END DESC
     LIMIT :pageSize OFFSET :page
    """
  )
  suspend fun getAllPagedDesc(
    page: Int,
    pageSize: Int,
    filter: String?,
    streamerIds: Collection<StreamerId>?,
    allStreamers: Boolean,
    dateStart: Long?,
    dateEnd: Long?,
    sortColumn: String,
  ): List<StreamDataWithStreamer>

  /**
   * Count all stream data that match the filter
   * @param filter filter string
   * @param streamerIds stream ids
   * @param allStreamers whether to show all streamers
   * @param dateStart start date
   * @param dateEnd end date
   * @return count of stream data entities
   */
  @Query(
    """
     SELECT COUNT(*) FROM StreamData
     WHERE (:allStreamers OR streamerId IN (:streamerIds))
     AND (:filter IS NULL OR title LIKE '%' || :filter || '%' OR outputFilePath LIKE '%' || :filter || '%' OR danmuFilePath LIKE '%' || :filter || '%')
     AND (:dateStart IS NULL OR dateStart >= :dateStart)
     AND (:dateEnd IS NULL OR dateEnd <= :dateEnd )
    """
  )
  suspend fun countAllStreamData(
    filter: String?,
    streamerIds: Collection<StreamerId>?,
    allStreamers: Boolean,
    dateStart: Long?,
    dateEnd: Long?,
  ): Long

  /**
   * Find stream data by stream id
   * @param streamerId stream id
   * @return list of stream data entities
   */
  @Transaction
  @Query("SELECT * FROM StreamData WHERE streamerId = :streamerId ORDER BY dateStart DESC")
  suspend fun findByStreamerIdWithStreamer(streamerId: StreamerId): List<StreamDataWithStreamer>
}