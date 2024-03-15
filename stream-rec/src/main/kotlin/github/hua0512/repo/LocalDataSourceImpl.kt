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

import github.hua0512.dao.AppConfigDao
import github.hua0512.dao.UserDao
import github.hua0512.data.config.AppConfig
import github.hua0512.logger
import github.hua0512.utils.UserEntity
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * @author hua0512
 * @date : 2024/2/18 23:55
 */
class LocalDataSourceImpl(private val dao: AppConfigDao, private val userDao: UserDao, private val json: Json) : LocalDataSource {
  override suspend fun streamAppConfig(): Flow<AppConfig> {
    return dao.streamLatestAppConfig()?.map {
      AppConfig(it, json)
    } ?: emptyFlow()
  }


  override suspend fun getAppConfig(): AppConfig {
    return withIOContext {
      dao.getLatestAppConfig()?.let { AppConfig(it, json) } ?: AppConfig().apply {
        logger.info("First time running the app, creating default app config")
        id = 1
        val user = UserEntity(1, "stream-rec", "stream-rec", "ADMIN")
        userDao.createUser(user)
        saveAppConfig(this)
      }
    }
  }

  override fun getPath(): String {
    return LocalDataSource.getDefaultPath()
  }

  override suspend fun saveAppConfig(appConfig: AppConfig) {
    return appConfig.run {
      dao.upsert(appConfig.toEntity(json))
    }
  }
}