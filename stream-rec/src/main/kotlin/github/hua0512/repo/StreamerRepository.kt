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
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.logger
import github.hua0512.plugins.download.Douyin
import github.hua0512.plugins.download.Huya
import github.hua0512.repo.streamer.StreamerRepo
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
class StreamerRepository(val dao: StreamerDao, val json: Json) : StreamerRepo {

  override suspend fun stream() = dao.stream()
    .map { items ->
      items.map {
        it.toStreamer(json).apply {
          populateTemplateStreamer()
        }
      }
    }
    .flowOn(Dispatchers.IO)

  override suspend fun getStreamers(): List<Streamer> {
    return withIOContext {
      dao.getAllStreamers().map {
        it.toStreamer(json).apply {
          populateTemplateStreamer()
        }
      }
    }
  }

  override suspend fun getAllTemplateStreamers(): List<Streamer> {
    return withIOContext {
      dao.getAllTemplateStreamers().map {
        it.toStreamer(json).apply {
          populateTemplateStreamer()
        }
      }
    }
  }

  override suspend fun getAllNonTemplateStreamers(): List<Streamer> {
    return withIOContext {
      dao.getAllNonTemplateStreamers().map {
        it.toStreamer(json).apply {
          populateTemplateStreamer()
        }
      }
    }
  }

  override suspend fun getStreamerById(id: Long): Streamer? {
    return withIOContext {
      dao.getStreamerById(StreamerId(id))?.toStreamer(json)?.apply {
        populateTemplateStreamer()
      }
    }
  }

  override suspend fun getStreamersActive(): List<Streamer> {
    return withIOContext {
      dao.getAllStremersActive().map {
        it.toStreamer(json).apply {
          populateTemplateStreamer()
        }
      }
    }
  }

  override suspend fun getStreamersInactive(): List<Streamer> {
    return withIOContext {
      dao.getAllStremersInactive().map {
        it.toStreamer(json).apply {
          populateTemplateStreamer()
        }
      }
    }
  }

  override suspend fun findStreamerByUrl(url: String): Streamer? {
    return withIOContext {
      dao.findStreamerByUrl(url)?.toStreamer(json)?.apply {
        populateTemplateStreamer()
      }
    }
  }


  override suspend fun insertOrUpdate(newStreamer: Streamer) {
    return withIOContext {
      val downloadConfig = if (newStreamer.downloadConfig != null) {
        json.encodeToString(newStreamer.downloadConfig)
      } else null

      val platform = if (newStreamer.platform == StreamingPlatform.UNKNOWN) {
        getPlatformByUrl(newStreamer.url)
      } else {
        newStreamer.platform
      }
      dao.updateStreamer(
        name = newStreamer.name,
        url = newStreamer.url,
        platform = platform.id.toLong(),
        isLive = newStreamer.isLive.asLong,
        isActive = newStreamer.isActivated.asLong,
        avatar = newStreamer.avatar,
        description = newStreamer.streamTitle,
        isTemplate = newStreamer.isTemplate.asLong,
        templateId = newStreamer.templateId,
        downloadConfig = downloadConfig
      )
      logger.debug("updatedStreamer: {}", newStreamer)
    }
  }

  override suspend fun saveStreamer(newStreamer: Streamer) {
    return withIOContext {
      val downloadConfig = if (newStreamer.downloadConfig != null) {
        json.encodeToString(newStreamer.downloadConfig)
      } else null

      val platform = if (newStreamer.platform == StreamingPlatform.UNKNOWN) {
        getPlatformByUrl(newStreamer.url)
      } else {
        newStreamer.platform
      }
      dao.insertStreamer(
        name = newStreamer.name,
        url = newStreamer.url,
        platform = platform.id.toLong(),
        isLive = newStreamer.isLive.asLong,
        isActive = newStreamer.isActivated.asLong,
        description = newStreamer.streamTitle,
        avatar = newStreamer.avatar,
        isTemplate = newStreamer.isTemplate.asLong,
        templateId = newStreamer.templateId,
        downloadConfig = downloadConfig
      )
      logger.debug("saveStreamer: {}, downloadConfig: {}", newStreamer, downloadConfig)
    }
  }

  private fun getPlatformByUrl(url: String): StreamingPlatform = when {
    Huya.REGEX.toRegex().find(url) != null -> StreamingPlatform.HUYA
    Douyin.REGEX.toRegex().find(url) != null -> StreamingPlatform.DOUYIN
    else -> StreamingPlatform.UNKNOWN
  }

  override suspend fun deleteStreamer(oldStreamer: Streamer) {
    if (oldStreamer.id == 0L) throw IllegalArgumentException("Streamer id is 0")
    return withIOContext {
      dao.deleteStreamer(StreamerId(oldStreamer.id))
      logger.debug("deletedStreamer: {}", oldStreamer)
    }
  }

  override suspend fun deleteStreamerById(id: Long) {
    return withIOContext {
      dao.deleteStreamer(StreamerId(id))
    }
  }

  /**
   * Change streamer active status
   * @param id streamer id
   * @param status true: active, false: inactive
   */
  override suspend fun updateStreamerLiveStatus(id: Long, status: Boolean) {
    return withIOContext {
      dao.updateStreamStatus(StreamerId(id), status.asLong)
    }
  }

  override suspend fun updateStreamerStreamTitle(id: Long, streamTitle: String?) {
    return withIOContext {
      dao.updateStreamTitle(StreamerId(id), streamTitle)
    }
  }

  override suspend fun updateStreamerAvatar(id: Long, avatar: String?) {
    return withIOContext {
      dao.updateAvatar(StreamerId(id), avatar)
    }
  }

  private suspend fun Streamer.populateTemplateStreamer() {
    if (!isTemplate && templateId != null && templateId != -1L) {
      templateStreamer = getStreamerById(templateId!!)
    }
  }
}