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

/**
 * @author hua0512
 * @date : 2024/2/18 16:07
 */
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import github.hua0512.StreamRecDatabase
import github.hua0512.dao.BaseDaoImpl
import github.hua0512.data.StreamerId
import github.hua0512.utils.StreamerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow


class StreamerDaoImpl(override val database: StreamRecDatabase) : BaseDaoImpl, StreamerDao {
  override suspend fun stream(): Flow<List<StreamerEntity>> {
    // get all streamers and map to list of Streamer
    return queries.selectAll().asFlow().mapToList(Dispatchers.IO)
  }

  override suspend fun getAllStreamers(): List<StreamerEntity> {
    return queries.selectAll().executeAsList()
  }

  override suspend fun findStreamerByUrl(url: String): StreamerEntity? {
    return queries.findStreamerByUrl(url).executeAsOneOrNull()
  }

  override suspend fun getAllStremersActive(): List<StreamerEntity> {
    return queries.selectAllActive().executeAsList()
  }


  override suspend fun getStreamerById(id: StreamerId): StreamerEntity? {
    val queriedStreamer = queries.getStreamerById(id.value).executeAsOneOrNull()
    return queriedStreamer
  }


  override suspend fun insertStreamer(
    name: String,
    url: String,
    platform: Long,
    isLive: Long,
    isActive: Long,
    downloadConfig: String?,
  ) {
    return queries.insertStreamer(
      name = name,
      url = url,
      platform = platform,
      is_live = isLive,
      is_active = isActive,
      download_config = downloadConfig
    )
  }

  override suspend fun changeStreamerLiveStatus(id: StreamerId, isLive: Long) {
    if (id.value == -1L) {
      return
    }
    return queries.changeStreamerLiveStatus(isLive, id.value)
  }

  override suspend fun updateStreamer(
    name: String,
    url: String,
    platform: Long,
    isLive: Long,
    isActive: Long,
    downloadConfig: String?,
  ) {
    return queries.upsertStreamer(
      name = name,
      url = url,
      platform = platform,
      is_live = isLive,
      is_active = isActive,
      download_config = downloadConfig,
    )
  }

  override suspend fun deleteStreamer(id: StreamerId) {
    return queries.deleteStreamer(id.value)
  }
}