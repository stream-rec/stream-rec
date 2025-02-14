package github.hua0512.data.platform

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import github.hua0512.data.config.AppConfigEntity
import github.hua0512.data.stream.StreamingPlatform

@Entity(
  tableName = "platform_config",
  foreignKeys = [
    ForeignKey(
      entity = AppConfigEntity::class,
      parentColumns = ["id"],
      childColumns = ["app_config_id"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("app_config_id")]
)
data class GlobalPlatformConfigEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,

  @ColumnInfo(name = "app_config_id")
  val appConfigId: Int,

  @ColumnInfo(name = "platform")
  val platform: StreamingPlatform,

  // Common fields across platforms
  @ColumnInfo(name = "fetch_delay")
  val fetchDelay: Long? = null,

  @ColumnInfo(name = "download_check_interval")
  val downloadCheckInterval: Long? = null,

  @ColumnInfo(name = "cookies")
  val cookies: String? = null,

  // Platform specific settings as JSON
  @ColumnInfo(name = "extra_config")
  val extraConfig: String? = null,
)