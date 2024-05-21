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

import androidx.room.Dao
import androidx.room.Query
import github.hua0512.dao.BaseDao
import github.hua0512.data.StreamerId
import github.hua0512.data.stream.entity.StreamerEntity
import kotlinx.coroutines.flow.Flow


/**
 * DAO for stream
 * @author hua0512
 * @date : 2024/2/18 16:05
 */
@Dao
interface StreamerDao : BaseDao<StreamerEntity> {

  @Query("SELECT * FROM streamer")
  fun stream(): Flow<List<StreamerEntity>>

  @Query("SELECT * FROM streamer")
  suspend fun getAll(): List<StreamerEntity>

  @Query("SELECT * FROM streamer WHERE url = :url")
  suspend fun findByUrl(url: String): StreamerEntity?

  @Query("SELECT * FROM streamer WHERE is_template = 0 AND template_id = :templateId")
  suspend fun findByTemplateId(templateId: StreamerId): List<StreamerEntity>

  @Query("SELECT COUNT(*) FROM streamer WHERE is_template = 0 AND template_id = :templateId")
  suspend fun countByTemplateId(templateId: StreamerId): Long

  @Query("SELECT * FROM streamer WHERE is_active = 1 AND is_template = 0")
  suspend fun getActivesNonTemplates(): List<StreamerEntity>

  @Query("SELECT * FROM streamer WHERE is_active = 0 AND is_template = 0")
  suspend fun getInactivesNonTemplates(): List<StreamerEntity>

  @Query("SELECT * FROM streamer WHERE is_template = 1")
  suspend fun getTemplates(): List<StreamerEntity>

  @Query("SELECT * FROM streamer WHERE is_template = 0")
  suspend fun getNonTemplates(): List<StreamerEntity>

  @Query("SELECT * FROM streamer WHERE id = :id")
  suspend fun getById(id: StreamerId): StreamerEntity?

  @Query("SELECT name FROM streamer WHERE id = :id")
  suspend fun getNameById(id: StreamerId): String?

}