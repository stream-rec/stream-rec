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

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import github.hua0512.StreamRecDatabase
import github.hua0512.data.config.*
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stats.StatsEntity
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.data.stream.entity.StreamDataEntity
import github.hua0512.data.stream.entity.StreamerEntity
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadState
import github.hua0512.data.upload.entity.UploadActionEntity
import github.hua0512.data.upload.entity.UploadDataEntity
import github.hua0512.data.upload.entity.UploadResultEntity
import github.hua0512.data.user.UserEntity
import github.hua0512.logger
import github.hua0512.repo.LocalDataSource
import github.hua0512.utils.boolean
import github.hua0512.utils.deleteFile
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Migrate data from sqldelight to room
 * @author hua0512
 * @date : 2024/5/21 14:31
 */


@Deprecated("Remove in the next release")
suspend fun startMigration(appDatabase: AppDatabase, json: Json) {
  // check if bak file exists
  val path = Path(LocalDataSource.getDefaultPath().plus(".bak"))
  if (!path.exists()) {
    logger.info("Sqldelight db not found, skipping migration...")
    return
  }

  val driver = LogSqliteDriver(
    sqlDriver = JdbcSqliteDriver("jdbc:sqlite:${path.pathString}", Properties().apply {
      put("foreign_keys", "true")
    }),
    logger = { logger.trace(it) }
  )

  StreamRecDatabase.Schema.create(driver)
  val dbVersion = LocalDataSource.getDbVersion()
  val schemaVersion = StreamRecDatabase.Schema.version
  logger.info("Database version: $dbVersion")

  try {
    if (dbVersion < schemaVersion) {
      logger.info("Trying to migrate database from version $dbVersion to $schemaVersion")
      StreamRecDatabase.Schema.migrate(driver, dbVersion.toLong(), schemaVersion)
      LocalDataSource.writeDbVersion(schemaVersion.toInt())
    }
  } catch (e: Exception) {
    logger.error("Failed to migrate database", e)
  }
  val database = StreamRecDatabase(driver = driver)

  val dao: SqldelightDao = SqldelightDaoImpl(database)
  logger.info("Migrating app configs...")
  val configs = dao.getAppConfigs().map {
    AppConfigEntity(
      it.id.toInt(),
      it.engine,
      it.danmu.boolean,
      it.outputFolder ?: "",
      it.outputFileName,
      VideoFormat.valueOf(it.outputFileFormat),
      it.minPartSize,
      it.maxPartSize,
      it.maxPartDuration,
      it.maxDownloadRetries.toInt(),
      it.downloadRetryDelay,
      it.downloadCheckInterval,
      it.maxConcurrentDownloads.toInt(),
      it.maxConcurrentUploads.toInt(),
      it.deleteFilesAfterUpload.boolean,
      it.useBuiltInSegmenter.boolean,
      false,
      json.decodeFromString(HuyaConfigGlobal.serializer(), it.huyaConfig.toString()),
      json.decodeFromString(DouyinConfigGlobal.serializer(), it.douyinConfig.toString()),
      json.decodeFromString(DouyuConfigGlobal.serializer(), it.douyuConfig.toString()),
      json.decodeFromString(TwitchConfigGlobal.serializer(), it.twitchConfig.toString()),
      json.decodeFromString(PandaTvConfigGlobal.serializer(), it.pandaliveConfig.toString())
    )
  }
  appDatabase.getConfigDao().insert(configs)
  logger.info("App configs migrated")

  logger.info("Migrating users...")
  val users = dao.getUsers().map {
    UserEntity(
      it.id,
      it.username,
      it.password,
      it.role,
      true
    )
  }
  appDatabase.getUserDao().insert(users)
  logger.info("Users migrated")

  logger.info("Migrating stats...")
  val stats = dao.getStats().map {
    StatsEntity(
      it.id,
      it.time,
      it.totalStreams,
      it.totalUploads,
      it.totalFailedUploads
    )
  }
  appDatabase.getStatsDao().insert(stats)
  logger.info("Stats migrated")
  logger.info("Migrating streamers...")
  val streamers = dao.getStreamers().map {
    StreamerEntity(
      it.streamer_id,
      it.name,
      it.url,
      StreamingPlatform.fromId(it.platform.toInt())!!,
      it.last_stream ?: 0,
      it.is_live.boolean,
      it.is_active.boolean,
      it.avatar,
      it.description,
      json.decodeFromString<DownloadConfig>(it.download_config.toString()),
      it.is_template.boolean,
      if (it.template_id == -1L) 0L else it.template_id ?: 0L,
      it.app_config_id ?: 1L
    )
  }
  appDatabase.getStreamerDao().insert(streamers)
  logger.info("Streamers migrated")

  logger.info("Migrating stream data...")
  val streams = dao.getStreamDatas().map {
    StreamDataEntity(
      it.id,
      it.title,
      it.dateStart,
      it.dateEnd,
      it.outputFilePath,
      it.danmuFilePath,
      it.outputFileSize,
      it.streamerId
    )
  }
  appDatabase.getStreamDataDao().insert(streams)
  logger.info("Stream data migrated...")
  logger.info("Migrating upload actions...")
  val actions = dao.getUploadActions().map {
    UploadActionEntity(
      it.id,
      it.time,
      json.decodeFromString<UploadConfig>(it.uploadConfig)
    )
  }
  appDatabase.getUploadActionDao().insert(actions)
  logger.info("Upload actions migrated")
  logger.info("Migrating upload data...")

  val actionFiles = dao.getUploadActionFiles()
  val uploadDatas = dao.getUploadDatas().mapNotNull {
    val files = actionFiles.firstOrNull { file -> file.uploadDataId == it.id } ?: return@mapNotNull null
    UploadDataEntity(
      it.id,
      it.filePath,
      UploadState.fromId(it.status.toInt()),
      it.streamDataId!!,
      files.uploadActionId,
    )
  }
  appDatabase.getUploadDataDao().insert(uploadDatas)
  logger.info("Upload data migrated")
  logger.info("Migrating upload results...")
  val results = dao.getUploadResults().map {
    UploadResultEntity(
      it.id,
      it.startTime,
      it.endTime,
      it.isSuccess.boolean,
      it.message.toString(),
      it.uploadDataId
    )
  }
  appDatabase.getUploadResultDao().insert(results)
  logger.info("Migration completed")
  LocalDataSource.writeDbType("room")
  LocalDataSource.writeDbVersion(schemaVersion.toInt())
  driver.close()
  // delete bak file
  path.deleteFile()
}