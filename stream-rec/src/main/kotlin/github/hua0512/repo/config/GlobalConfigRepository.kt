package github.hua0512.repo.config

import github.hua0512.dao.config.GlobalPlatformConfigDao
import github.hua0512.data.config.GlobalPlatformConfig
import kotlinx.coroutines.flow.Flow

class GlobalConfigRepository(override val dao: GlobalPlatformConfigDao) : GlobalPlatformConfigRepo {

  override suspend fun getGlobalPlatformConfig(): GlobalPlatformConfig {
    return dao.getGlobalPlatformConfig()
  }

  override suspend fun saveGlobalPlatformConfig(globalPlatformConfig: GlobalPlatformConfig) {
    TODO("Not yet implemented")
  }

  override suspend fun streamGlobalPlatformConfig(): Flow<GlobalPlatformConfig> {
    TODO("Not yet implemented")
  }
}