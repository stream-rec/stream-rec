package github.hua0512.repo.config

import github.hua0512.dao.config.GlobalPlatformConfigDao
import github.hua0512.data.config.GlobalPlatformConfig
import github.hua0512.data.platform.GlobalPlatformConfigEntity
import github.hua0512.repo.IRepo
import kotlinx.coroutines.flow.Flow

interface GlobalPlatformConfigRepo : IRepo<GlobalPlatformConfigDao, GlobalPlatformConfigEntity> {

  suspend fun getGlobalPlatformConfig(): GlobalPlatformConfig

  suspend fun saveGlobalPlatformConfig(globalPlatformConfig: GlobalPlatformConfig)

  suspend fun streamGlobalPlatformConfig(): Flow<GlobalPlatformConfig>
}