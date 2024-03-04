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

package github.hua0512.dao.stats

import github.hua0512.StreamRecDatabase
import github.hua0512.dao.BaseDaoImpl
import github.hua0512.utils.StatsEntity

/**
 * Summary stats dao implementation
 * @author hua0512
 * @date : 2024/3/4 10:40
 */
class StatsDaoImpl(override val database: StreamRecDatabase) : BaseDaoImpl, StatsDao {
  override fun getStatsFromTo(from: Long, to: Long): List<StatsEntity> {
    return database.streamerQueries.getStatsByTimeRange(from, to).executeAsList()
  }

  override fun getStatsFromToWithLimit(from: Long, to: Long, limit: Int): List<StatsEntity> {
    return database.streamerQueries.getStatsByTimeRangeWithLimit(from, to, limit.toLong()).executeAsList()
  }

  override fun getStatsFrom(from: Long): List<StatsEntity> {
    return database.streamerQueries.getStatsByTimeRange(from, Long.MAX_VALUE).executeAsList()
  }

  override fun getStatsTo(to: Long): List<StatsEntity> {
    return database.streamerQueries.getStatsByTimeRange(0, to).executeAsList()
  }

  override fun getStats(): List<StatsEntity> {
    return getStatsTo(Long.MAX_VALUE)
  }

  override fun insertStats(stats: StatsEntity) {
    return database.streamerQueries.insertStats(
      stats.time,
      stats.totalStreams,
      stats.totalUploads,
      stats.totalFailedUploads
    )
  }

  override fun updateStats(stats: StatsEntity) {
    return database.streamerQueries.updateStats(
      stats.totalStreams,
      stats.totalUploads,
      stats.totalFailedUploads,
      stats.id
    )
  }

  override fun deleteStats(stats: StatsEntity) {
    return database.streamerQueries.deleteStats(stats.id)
  }
}