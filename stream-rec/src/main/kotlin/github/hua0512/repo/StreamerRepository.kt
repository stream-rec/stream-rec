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

import github.hua0512.dao.stream.StreamerDao
import github.hua0512.data.StreamerId
import github.hua0512.data.stream.Streamer
import github.hua0512.logger
import github.hua0512.utils.asLong
import github.hua0512.utils.toStreamer
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * @author hua0512
 * @date : 2024/2/18 13:45
 */
class StreamerRepository(val dao: StreamerDao, val json: Json) {


  suspend fun stream() = dao.stream()
    .map { items ->
      items.map { it.toStreamer(json) }
    }
    .flowOn(Dispatchers.IO)

  suspend fun getStreamers(): List<Streamer> {
    return withIOContext {
      dao.getAllStreamers().map { it.toStreamer(json) }
    }
  }

  suspend fun getStreamersActive(): List<Streamer> {
    return withIOContext {
      dao.getAllStremersActive().map { it.toStreamer(json) }
    }
  }


  suspend fun findStreamerByUrl(url: String): Streamer? {
    return withIOContext {
      dao.findStreamerByUrl(url)?.toStreamer(json)
    }
  }


  suspend fun insertOrUpdate(streamer: Streamer) {
    return withIOContext {
      val downloadConfig = if (streamer.downloadConfig != null) {
        json.encodeToString(streamer.downloadConfig)
      } else null

      dao.updateStreamer(
        name = streamer.name,
        url = streamer.url,
        platform = streamer.platform.id.toLong(),
        isLive = streamer.isLive.asLong,
        isActive = streamer.isActivated.asLong,
        downloadConfig = downloadConfig
      )
      logger.debug("updatedStreamer: {}", streamer)
    }
  }

  suspend fun saveStreamer(newStreamer: Streamer) {

    return withIOContext {
      val downloadConfig = if (newStreamer.downloadConfig != null) {
        json.encodeToString(newStreamer.downloadConfig)
      } else null

      dao.insertStreamer(
        newStreamer.name,
        newStreamer.url,
        newStreamer.platform.id.toLong(),
        newStreamer.isLive.asLong,
        newStreamer.isActivated.asLong,
        downloadConfig
      )
      logger.debug("saveStreamer: {}, downloadConfig: {}", newStreamer, downloadConfig)
    }
  }

  suspend fun deleteStreamer(oldStreamer: Streamer) {
    if (oldStreamer.id == 0L) throw IllegalArgumentException("Streamer id is 0")
    return withIOContext {
      dao.deleteStreamer(StreamerId(oldStreamer.id))
      logger.debug("deletedStreamer: {}", oldStreamer)
    }
  }

  /**
   * Change streamer active status
   * @param id streamer id
   * @param status true: active, false: inactive
   */
  suspend fun changeStreamerLiveStatus(id: Long, status: Boolean) {
    return withIOContext {
      dao.changeStreamerLiveStatus(StreamerId(id), status.asLong)
    }
  }


}