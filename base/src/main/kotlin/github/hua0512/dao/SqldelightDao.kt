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

package github.hua0512.dao

import github.hua0512.StreamRecDatabase
import github.hua0512.sqldelight.db.StreamerQueries
import github.hua0512.utils.*


/**
 * SqlDelight DAO interface
 * @author hua0512
 * @date : 2024/5/21 13:54
 */

@Deprecated("Deprecated in favor of Room database")
interface SqldelightDao {

  val database: StreamRecDatabase

  val queries: StreamerQueries
    get() = database.streamerQueries

  suspend fun getAppConfigs(): List<AppConfigEntity>

  suspend fun getStreamers(): List<StreamerEntity>

  suspend fun getStreamDatas(): List<StreamDataEntity>

  suspend fun getUploadDatas(): List<UploadDataEntity>

  suspend fun getUploadResults(): List<UploadResultEntity>

  suspend fun getUploadActions(): List<UploadActionEntity>

  suspend fun getUploadActionFiles(): List<UploadActionFilesEntity>

  suspend fun getUsers(): List<UserEntity>

  suspend fun getStats(): List<StatsEntity>
}

@Deprecated("Deprecated in favor of Room database")
class SqldelightDaoImpl(override val database: StreamRecDatabase) : SqldelightDao {

  override suspend fun getAppConfigs(): List<AppConfigEntity> = withIOContext { queries.selectAllAppConfig().executeAsList() }

  override suspend fun getStreamers(): List<StreamerEntity> = withIOContext { queries.selectAll().executeAsList() }

  override suspend fun getStreamDatas(): List<StreamDataEntity> = withIOContext { queries.selectAllStreamData().executeAsList() }

  override suspend fun getUploadDatas(): List<UploadDataEntity> = withIOContext { queries.selectAllUploadData().executeAsList() }

  override suspend fun getUploadResults(): List<UploadResultEntity> = withIOContext { queries.selectAllUploadResult().executeAsList() }

  override suspend fun getUploadActions(): List<UploadActionEntity> = withIOContext { queries.selectAllUploadActions().executeAsList() }
  override suspend fun getUploadActionFiles(): List<UploadActionFilesEntity> = withIOContext { queries.selectAllUploadActionFiles().executeAsList() }

  override suspend fun getUsers(): List<UserEntity> = withIOContext { queries.getAllUsers().executeAsList() }

  override suspend fun getStats(): List<StatsEntity> = withIOContext { queries.selectAllStats().executeAsList() }


}