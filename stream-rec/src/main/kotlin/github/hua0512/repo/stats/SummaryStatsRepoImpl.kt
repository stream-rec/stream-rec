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

package github.hua0512.repo.stats

import github.hua0512.dao.stats.StatsDao
import github.hua0512.data.stats.Stats
import github.hua0512.data.stats.StatsEntity
import github.hua0512.data.stats.SummaryStats
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * @author hua0512
 * @date : 2024/3/4 10:44
 */
class SummaryStatsRepoImpl(private val statsDao: StatsDao) : SummaryStatsRepo {

  override suspend fun getSummaryStats(): SummaryStats {
    val stats = statsDao.getAllByTimeDesc()
    return SummaryStats(
      stats.sumOf { it.streams },
      0,
      stats.sumOf { it.uploads },
      0,
      stats.map { Stats(it) }
    )
  }

  override suspend fun getSummaryStatsFromTo(from: Long, to: Long): SummaryStats {
    val diff = Instant.fromEpochSeconds(to) - Instant.fromEpochSeconds(from)

    val stats = if (diff.inWholeSeconds < 30 * 24 * 60 * 60) {
      statsDao.getBetweenTimeOrderedDesc(from, to)
    } else {
      statsDao.getBetweenTimeOrderedDesc(from, to)
        .groupByMonthAndYear()
        .mergeStatsWithinMonth()
    }

    val previous = statsDao.getBetweenTimeOrderedDesc(from - diff.inWholeSeconds, to - diff.inWholeSeconds)

    return SummaryStats(
      stats.sumOf { it.streams },
      previous.sumOf { it.streams },
      stats.sumOf { it.uploads },
      previous.sumOf { it.uploads },
      stats.map { Stats(it) }
    )
  }

  private fun List<StatsEntity>.groupByMonthAndYear() = groupBy {
    val dateTime = Instant.fromEpochSeconds(it.timeStamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
    dateTime.year to dateTime.monthNumber
  }

  private fun Map<Pair<Int, Int>, List<StatsEntity>>.mergeStatsWithinMonth() = flatMap {
    val first = it.value.first()
    val firstDay = Instant.fromEpochSeconds(first.timeStamp)
      .toLocalDateTime(TimeZone.currentSystemDefault()).date.atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds

    val monthCurrent = it.value.reduce { acc, statsEntity ->
      acc.copy(
        streams = acc.streams + statsEntity.streams,
        uploads = acc.uploads + statsEntity.uploads,
        timeStamp = firstDay
      )
    }
    listOf(monthCurrent)
  }

  override suspend fun getStatsFromTo(from: Long, to: Long): List<Stats> {
    return statsDao.getBetweenTimeOrderedDesc(from, to).map { Stats(it) }
  }

  override suspend fun getStatsFrom(from: Long): List<Stats> {
    return statsDao.getFromOrderedByTimeDesc(from).map { Stats(it) }
  }

}