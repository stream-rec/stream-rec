/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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

package github.hua0512.repo.config

import github.hua0512.dao.config.EngineConfigDao
import github.hua0512.data.config.engine.EngineConfig
import github.hua0512.data.config.engine.EngineConfigEntity
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Repository for engine config data
 * @author hua0512
 * @date : 2/22/2025 1:05 PM
 */
class EngineConfigManager(override val dao: EngineConfigDao, val json: Json) : EngineConfigRepo {
  val configCache = mutableMapOf<String, EngineConfig>()
  val mutex = Mutex()


  // Fetch engine configuration by config ID and engine type
  override suspend fun <T : EngineConfig> getEngineConfig(configId: Int, engineType: String): T = withIOContext {
    val cacheKey = "$configId-$engineType"
    mutex.withLock {
      // Check if the configuration is already cached, if so, return it
      configCache[cacheKey]?.let {
        return@withLock it as T
      }

      // If not cached, fetch from the repository
      val configEntity = dao.getEngineConfig(configId, engineType)

      val config = configEntity?.let { json.decodeFromString<EngineConfig>(it.config) }
        ?: throw NoSuchElementException("No config found for $engineType")

      // Cache the fetched configuration
      configCache[cacheKey] = config
      config as T
    }
  }


  // Update engine configuration
  override suspend fun <T : EngineConfig> updateEngineConfig(configId: Int, config: T): T = withIOContext {
    mutex.withLock {
      // Serialize the configuration
      val configJson = json.encodeToString<EngineConfig>(config)
      val engineName = config.getName()
      val configEntity = EngineConfigEntity(configId = configId, engineType = engineName, config = configJson)
      dao.upsert(configEntity)

      // Update the cache
      val cacheKey = "$configId-${engineName}"
      configCache[cacheKey] = config
    }
    config
  }

  // Delete engine configuration
  suspend fun deleteEngineConfig(id: Int, configId: Int, engineType: String) {
    mutex.withLock {

      val cacheKey = "$configId-$engineType"

      configCache.remove(cacheKey)?.let {
        dao.deleteEngineConfig(configId, engineType)
      }
    }
  }

  // Clear the cache
  suspend fun clearCache() {
    mutex.withLock {
      configCache.clear()
    }
  }

  /**
   * Stream engine configurations by config ID
   * @param configId The ID of the configuration to stream
   * @return A flow of updated engine configurations
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun streamConfigs(configId: Int): Flow<EngineConfig> {
    return dao.streamConfigs(configId)
      .flowOn(Dispatchers.IO)
      .flatMapConcat { configEntities ->
        configEntities.map { entity ->
          val config = json.decodeFromString<EngineConfig>(entity.config)
          config
        }.mapNotNull {
          // check if the config is already in the cache
          // update the cache if not, or skip if it is
          mutex.withLock {
            val cacheKey = "$configId-${it.getName()}"
            // update the cache if not
            if (!configCache.containsKey(cacheKey) || configCache[cacheKey] != it) {
              configCache[cacheKey] = it
              it
            } else
            // we skip the flow if the config is already in the cache
              null
          }
        }.asFlow()
      }.flowOn(Dispatchers.Default)

  }
}