package github.hua0512.dao.config

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import github.hua0512.dao.BaseDao
import github.hua0512.data.platform.GlobalPlatformConfigEntity
import github.hua0512.data.stream.StreamingPlatform
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalPlatformConfigDao : BaseDao<GlobalPlatformConfigEntity> {

  @Query("SELECT * FROM platform_config WHERE app_config_id = :appConfigId")
  suspend fun getAllForConfig(appConfigId: Int): List<GlobalPlatformConfigEntity>

  @Query("SELECT * FROM platform_config WHERE app_config_id = :appConfigId AND platform = :platform")
  suspend fun getForPlatform(appConfigId: Int, platform: StreamingPlatform): GlobalPlatformConfigEntity?

  @Transaction
  suspend fun updatePlatformConfig(config: GlobalPlatformConfigEntity) {
    upsert(config)
  }

  @Query("DELETE FROM platform_config WHERE app_config_id = :appConfigId AND platform = :platform")
  suspend fun deleteForPlatform(appConfigId: Int, platform: StreamingPlatform)

  @Query("SELECT * FROM platform_config WHERE app_config_id = :appConfigId")
  suspend fun streamGlobalPlatformConfig(appConfigId: Int): Flow<GlobalPlatformConfigEntity>
}