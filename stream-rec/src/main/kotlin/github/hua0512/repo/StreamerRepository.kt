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
import github.hua0512.data.stream.entity.StreamerEntity
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Streamer related actions repository
 * @author hua0512
 * @date : 2024/2/18 13:45
 */
class StreamerRepository(val dao: StreamerDao) : StreamerRepo {

  override suspend fun stream() = dao.stream()
    .map { items ->
      items.toStreamers()
    }
    .flowOn(Dispatchers.IO)

  override suspend fun getStreamers(): List<Streamer> = withIOContext {
    dao.getAll().toStreamers()
  }

  override suspend fun getAllTemplateStreamers(): List<Streamer> = withIOContext {
    dao.getTemplates().toStreamers()
  }

  override suspend fun getAllNonTemplateStreamers(): List<Streamer> = withIOContext {
    dao.getNonTemplates().toStreamers()
  }

  override suspend fun getStreamerById(id: StreamerId): Streamer? {
    return withIOContext {
      dao.getById(id)?.toStreamer()
    }
  }

  override suspend fun getStreamersActive(): List<Streamer> = withIOContext {
    dao.getActivesNonTemplates().map {
      it.toStreamer()
    }
  }

  override suspend fun getStreamersInactive(): List<Streamer> = withIOContext {
    dao.getInactivesNonTemplates().toStreamers()
  }

  override suspend fun findStreamerByUrl(url: String): Streamer? = withIOContext {
    dao.findByUrl(url)?.toStreamer()
  }

  override suspend fun findStreamersUsingTemplate(templateId: StreamerId): List<Streamer> = withIOContext {
    dao.findByTemplateId(templateId).map {
      it.toStreamer()
    }
  }

  override suspend fun countStreamersUsingTemplate(templateId: StreamerId): Long = withIOContext {
    dao.countByTemplateId(templateId)
  }

  override suspend fun update(streamer: Streamer) = withIOContext {
    dao.update(streamer.toStreamerEntity()) == 1
  }

  override suspend fun save(newStreamer: Streamer): Streamer = withIOContext {
    val result = dao.insert(newStreamer.toStreamerEntity())
    newStreamer.copy(id = result)
  }

  override suspend fun delete(oldStreamer: Streamer): Boolean {
    if (oldStreamer.id == 0L) throw IllegalArgumentException("Streamer id is 0")
    return withIOContext {
      dao.delete(oldStreamer.toStreamerEntity()) == 1
    }
  }

  private suspend fun Collection<StreamerEntity>.toStreamers(): List<Streamer> = map { it.toStreamer() }

  private suspend fun StreamerEntity.toStreamer(): Streamer {
    if (templateId > 0) {
      return Streamer(this, getStreamerById(StreamerId(templateId)))
    }
    return Streamer(this)
  }
}