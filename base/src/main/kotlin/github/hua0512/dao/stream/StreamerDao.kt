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

import github.hua0512.data.StreamerId
import github.hua0512.utils.StreamerEntity
import kotlinx.coroutines.flow.Flow


/**
 * @author hua0512
 * @date : 2024/2/18 16:05
 */
interface StreamerDao {

  suspend fun stream(): Flow<List<StreamerEntity>>
  suspend fun getAllStreamers(): List<StreamerEntity>

  suspend fun getStreamerNameById(id: StreamerId): String?

  suspend fun findStreamerByUrl(url: String): StreamerEntity?
  suspend fun findStreamersUsingTemplate(templateId: Long): List<StreamerEntity>
  suspend fun countStreamersUsingTemplate(templateId: Long): Long

  suspend fun getAllStremersActive(): List<StreamerEntity>

  suspend fun getAllStremersInactive(): List<StreamerEntity>

  suspend fun getAllTemplateStreamers(): List<StreamerEntity>

  suspend fun getAllNonTemplateStreamers(): List<StreamerEntity>

  suspend fun getStreamerById(id: StreamerId): StreamerEntity?
  suspend fun insertStreamer(
    name: String, url: String, platform: Long, lastStream: Long?, isLive: Long, isActive: Long, description: String?,
    avatar: String?, downloadConfig: String?,
    isTemplate: Long, templateId: Long?,
  )

  suspend fun updateStreamStatus(id: StreamerId, isLive: Long)
  suspend fun updateStreamTitle(id: StreamerId, streamTitle: String?)

  suspend fun updateAvatar(id: StreamerId, avatar: String?)

  suspend fun updateLastStream(id: StreamerId, lastStream: Long)

  suspend fun updateStreamer(
    name: String, url: String, platform: Long, lastStream: Long?, isLive: Long, isActive: Long, description: String?,
    avatar: String?, downloadConfig: String?,
    isTemplate: Long, templateId: Long?,
  )


  suspend fun deleteStreamer(id: StreamerId)
}