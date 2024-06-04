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

import github.hua0512.dao.stats.StatsDao
import github.hua0512.dao.stream.StreamDataDao
import github.hua0512.data.StreamDataId
import github.hua0512.data.StreamerId
import github.hua0512.data.stats.StatsEntity
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.entity.StreamDataEntity
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.utils.getTodayStart
import github.hua0512.utils.withIOContext

/**
 * Stream data repository
 * @author hua0512
 * @date : 2024/2/19 10:21
 */
class StreamDataRepository(val dao: StreamDataDao, private val statsDao: StatsDao) :
  StreamDataRepo {
  override suspend fun getStreamDataById(streamDataId: StreamDataId): StreamData? {
    return withIOContext {
      dao.getWithStreamerById(streamDataId.value)?.let {
        StreamData(it.streamData, Streamer(it.streamer))
      }
    }
  }

  override suspend fun getAllStreamData(): List<StreamData> = withIOContext {
    dao.getAllWithStreamer().flatMap { (t, u) -> u.map { StreamData(it, Streamer(t)) } }
  }

  override suspend fun getStreamDataPaged(
    page: Int,
    pageSize: Int,
    streamers: List<StreamerId>?,
    filter: String?,
    dateStart: Long?,
    dateEnd: Long?,
    sortColumn: String?,
    sortOrder: String?,
  ): List<StreamData> {
    return withIOContext {
      val order = sortOrder ?: "DESC"

      if (order != "ASC" && order != "DESC") {
        throw IllegalArgumentException("Invalid sortOrder: $order")
      }

      when (order) {
        "ASC" -> dao.getAllPagedAsc(
          (page - 1) * pageSize,
          pageSize,
          filter,
          streamers,
          streamers?.isEmpty() ?: true,
          dateStart,
          dateEnd,
          sortColumn ?: "dateStart"
        )

        "DESC" -> dao.getAllPagedDesc(
          (page - 1) * pageSize,
          pageSize,
          filter,
          streamers,
          streamers?.isEmpty() ?: true,
          dateStart,
          dateEnd,
          sortColumn ?: "dateStart"
        )

        else -> throw IllegalArgumentException("Invalid sortOrder: $order")
      }.map {
        StreamData(it.streamData, Streamer(it.streamer))
      }
    }
  }

  override suspend fun count(streamers: List<StreamerId>?, filter: String?, dateStart: Long?, dateEnd: Long?): Long {
    return withIOContext {
      dao.countAllStreamData(filter, streamers, streamers?.isEmpty() ?: true, dateStart, dateEnd)
    }
  }


  override suspend fun save(streamData: StreamData): StreamData {
    return withIOContext {
      val id = dao.insert(streamData.toEntity())

      // get today's timestamp
      val todayStart = getTodayStart()
      val todayStats = statsDao.getBetweenOrderedByTimeWithLimit(todayStart.epochSeconds, todayStart.epochSeconds, 1).firstOrNull()
      if (todayStats != null) {
        val newStats = todayStats.copy(streams = todayStats.streams + 1)
        statsDao.update(newStats)
      } else {
        statsDao.insert(StatsEntity(0, todayStart.epochSeconds, 1, 0, 0))
      }
      return@withIOContext streamData.copy(id = id)
    }
  }

  override suspend fun delete(id: StreamDataId) = withIOContext {
    val streamData = StreamDataEntity(id = id.value, "", outputFilePath = "")
    dao.delete(streamData) == 1
  }
}