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

import github.hua0512.StreamRecDatabase
import github.hua0512.dao.BaseDaoImpl
import github.hua0512.data.StreamDataId
import github.hua0512.data.StreamerId
import github.hua0512.data.UploadDataId
import github.hua0512.utils.UploadDataEntity

/**
 * @author hua0512
 * @date : 2024/2/18 16:07
 */
class UploadDataDaoImpl(override val database: StreamRecDatabase) : BaseDaoImpl, UploadDataDao {
  override fun getAllUploadData(): List<UploadDataEntity> {
    return queries.selectAllUploadData().executeAsList()
  }

  override fun getAllUploadDataPaginated(
    page: Int,
    pageSize: Int,
    filter: String,
    status: Collection<Long>?,
    streamerIds: Collection<StreamerId>?,
    allStreamers: Boolean?,
    sortColumn: String,
    sortOrder: String,
  ): List<UploadDataEntity> {

    val sortOrder = sortOrder.uppercase()
    if (sortOrder != "ASC" && sortOrder != "DESC") {
      throw IllegalArgumentException("Invalid sortOrder: $sortOrder")
    }

    val showAllStreamers = allStreamers ?: false

    val query = when (sortOrder) {
      "ASC" -> {
        queries.selectAllUploadDataPaginatedAsc(
          allStreamers = showAllStreamers,
          streamers = streamerIds?.map { it.value } ?: emptyList(),
          status = status ?: emptyList(),
          title = filter,
          filePath = filter,
          offset = (page - 1L) * pageSize,
          limit = pageSize.toLong(),
          sortColumn = sortColumn,
          mapper = { id, filePath, uploadStatus, streamDataId ->
            UploadDataEntity(
              id,
              filePath,
              streamDataId,
              uploadStatus,
            )
          })
      }

      "DESC" -> {
        queries.selectAllUploadDataPaginatedDesc(
          allStreamers = showAllStreamers,
          streamers = streamerIds?.map { it.value } ?: emptyList(),
          status = status ?: emptyList(),
          title = filter,
          filePath = filter,
          offset = (page - 1L) * pageSize,
          limit = pageSize.toLong(),
          sortColumn = sortColumn,
          mapper = { id, filePath, uploadStatus, streamDataId ->
            UploadDataEntity(
              id,
              filePath,
              streamDataId,
              uploadStatus,
            )
          })
      }

      else -> throw IllegalArgumentException("Invalid sortOrder: $sortOrder")
    }
    return query.executeAsList()
  }

  override fun countAllUploadData(status: Collection<Long>?, filter: String, streamerIds: Collection<StreamerId>?): Long {
    return queries.countAllUploadData(
      status = status ?: emptyList(),
      allStreamers = streamerIds.isNullOrEmpty(),
      streamers = streamerIds?.map { it.value } ?: emptyList(),
      title = filter,
      filePath = filter
    )
      .executeAsOneOrNull() ?: 0
  }


  override fun getUploadDatasByStatus(status: Long): List<UploadDataEntity> {
    return queries.selectAllUploadDataByStatus(status).executeAsList()
  }

  override fun getUploadDataById(id: UploadDataId): UploadDataEntity? {
    return queries.getUploadDataById(id.value).executeAsOneOrNull()
  }

  override fun insertUploadData(filePath: String, streamDataId: StreamDataId, status: Long): Long {
    queries.insertUploadData(filePath, streamDataId.value, status)
    return queries.getUploadDataIdByPath(filePath).executeAsOne()
  }

  override fun updateUploadDataStatus(id: UploadDataId, status: Long) {
    return queries.updateUploadDataStatus(status, id.value)
  }

  override fun deleteUploadData(id: UploadDataId) {
    return queries.deleteUploadData(id.value)
  }
}