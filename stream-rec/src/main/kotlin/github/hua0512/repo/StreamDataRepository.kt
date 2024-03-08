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
import github.hua0512.dao.stream.StreamerDao
import github.hua0512.data.StreamDataId
import github.hua0512.data.StreamerId
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.repo.streamer.StreamDataRepo
import github.hua0512.utils.StatsEntity
import github.hua0512.utils.getTodayStart
import github.hua0512.utils.withIOContext
import kotlinx.serialization.json.Json

/**
 * @author hua0512
 * @date : 2024/2/19 10:21
 */
class StreamDataRepository(val dao: StreamDataDao, private val streamerDao: StreamerDao, private val statsDao: StatsDao, private val json: Json) :
  StreamDataRepo {
  override suspend fun getStreamDataById(streamDataId: StreamDataId): StreamData? {
    return withIOContext {
      dao.getStreamDataById(streamDataId)?.let {
        StreamData(it).apply {
          populateStreamer()
        }
      }
    }
  }

  override suspend fun getAllStreamData(): List<StreamData> = withIOContext {
    dao.getAllStreamData().map { streamData ->
      StreamData(streamData).apply {
        populateStreamer()
      }
    }
  }

  override suspend fun getStremDataPaged(page: Int, pageSize: Int): List<StreamData> {
    return withIOContext {
      dao.getAllStreamDataPaged(page, pageSize).map {
        StreamData(it).apply {
          populateStreamer()
        }
      }
    }
  }

  override suspend fun getStreamDataByStreamerId(streamerId: StreamerId) = withIOContext {
    dao.findStreamDataByStreamerId(streamerId).firstOrNull()?.let {
      StreamData(it).apply {
        populateStreamer()
      }
    }
  }

  override suspend fun saveStreamData(streamData: StreamData): Long {
    return withIOContext {
      val id = dao.saveStreamData(streamData.toStreamDataEntity()).also {
        streamData.id = it
      }

      // get today's timestamp
      val todayStart = getTodayStart()
      val todayStats = statsDao.getStatsFromToWithLimit(todayStart.epochSeconds, todayStart.epochSeconds, 1).firstOrNull()
      if (todayStats != null) {
        val newStats = todayStats.copy(totalStreams = todayStats.totalStreams + 1)
        statsDao.updateStats(newStats)
      } else {
        statsDao.insertStats(StatsEntity(0, todayStart.epochSeconds, 1, 0, 0))
      }
      return@withIOContext id
    }
  }

  override suspend fun deleteStreamData(id: StreamDataId) {
    return withIOContext { dao.deleteStreamData(id) }
  }

  private suspend fun StreamData.populateStreamer() {
    streamer = streamerDao.getStreamerById(StreamerId(streamerId))?.let { Streamer(it, json) }
      ?: throw IllegalStateException("Streamer not found for streamData $id")
  }

}