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

import github.hua0512.StreamRecDatabase
import github.hua0512.dao.BaseDaoImpl
import github.hua0512.data.StreamDataId
import github.hua0512.data.StreamerId
import github.hua0512.utils.StreamDataEntity

/**
 * @author hua0512
 * @date : 2024/2/19 10:16
 */
class StreamDataDaoImpl(override val database: StreamRecDatabase) : BaseDaoImpl, StreamDataDao {
  override suspend fun getStreamDataById(streamDataId: StreamDataId): StreamDataEntity? {
    return queries.getStreamDataById(streamDataId.value).executeAsOneOrNull()
  }

  override suspend fun getAllStreamData(): List<StreamDataEntity> {
    return queries.selectAllStreamData().executeAsList()
  }


  private fun validateSortOrder(sortOrder: String) {
    val sortOrder = sortOrder.uppercase()
    if (sortOrder != "ASC" && sortOrder != "DESC") {
      throw IllegalArgumentException("Invalid sortOrder: $sortOrder")
    }
  }

  override suspend fun getAllStreamDataPaged(
    page: Int,
    pageSize: Int,
    filter: String?,
    streamerIds: Collection<StreamerId>?,
    allStreamers: Boolean?,
    dateStart: Long?,
    dateEnd: Long?,
    sortColumn: String,
    sortOrder: String,
  ): List<StreamDataEntity> {
    val sortOrder = sortOrder.uppercase()
    validateSortOrder(sortOrder)

    val showAllStreamers = allStreamers ?: streamerIds.isNullOrEmpty()

    val query = when (sortOrder) {
      "ASC" -> queries.selectAllStreamDataPagedAsc(
        offset = ((page - 1) * pageSize).toLong(),
        limit = pageSize.toLong(),
        allStreamers = showAllStreamers,
        streamerIds = streamerIds?.map { it.value } ?: emptyList(),
        title = filter,
        danmuFilePath = filter,
        outputFilePath = filter,
        sortColumn = sortColumn,
        dateStart = dateStart,
        dateEnd = dateEnd
      )

      "DESC" -> queries.selectAllStreamDataPagedDesc(
        offset = ((page - 1) * pageSize).toLong(),
        limit = pageSize.toLong(),
        allStreamers = showAllStreamers,
        streamerIds = streamerIds?.map { it.value } ?: emptyList(),
        title = filter,
        danmuFilePath = filter,
        outputFilePath = filter,
        sortColumn = sortColumn,
        dateStart = dateStart,
        dateEnd = dateEnd
      )

      else -> throw IllegalArgumentException("Invalid sortOrder: $sortOrder")
    }

    return query.executeAsList()
  }

  override suspend fun countAllStreamData(
    filter: String?,
    streamerIds: Collection<StreamerId>?,
    allStreamers: Boolean?,
    dateStart: Long?,
    dateEnd: Long?,
  ): Long {
    val showAllStreamers = allStreamers ?: streamerIds.isNullOrEmpty()
    return queries.countAllStreamData(
      allStreamers = showAllStreamers,
      streamerIds = streamerIds?.map { it.value } ?: emptyList(),
      title = filter,
      danmuFilePath = filter,
      outputFilePath = filter,
      dateStart = dateStart,
      dateEnd = dateEnd
    ).executeAsOne()
  }

  override suspend fun findStreamDataByStreamerId(streamerId: StreamerId): List<StreamDataEntity> {
    return queries.selectAllStreamDataOfStreamer(streamerId.value).executeAsList()
  }

  override suspend fun saveStreamData(streamData: StreamDataEntity): Long {
    return queries.transactionWithResult {
      queries.insertStreamData(
        streamData.title,
        streamData.dateStart,
        streamData.dateEnd,
        streamData.outputFilePath,
        streamData.danmuFilePath,
        streamData.outputFileSize,
        streamData.streamerId
      )
      // get the last inserted id
      queries.getStreamDataIdByOutputFilePath(streamData.outputFilePath).executeAsOne()
    }
  }

  override suspend fun deleteStreamData(streamData: StreamDataEntity) {
    return deleteStreamData(StreamDataId(streamData.id))
  }

  override suspend fun deleteStreamData(streamDataId: StreamDataId) {
    return queries.deleteStreamData(streamDataId.value)
  }
}