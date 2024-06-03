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

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import github.hua0512.dao.AppDatabase.Companion.DATABASE_VERSION
import github.hua0512.dao.config.AppConfigDao
import github.hua0512.dao.stats.StatsDao
import github.hua0512.dao.stream.StreamDataDao
import github.hua0512.dao.stream.StreamerDao
import github.hua0512.dao.upload.UploadActionDao
import github.hua0512.dao.upload.UploadDataDao
import github.hua0512.dao.upload.UploadResultDao
import github.hua0512.dao.user.UserDao
import github.hua0512.data.config.AppConfigEntity
import github.hua0512.data.stats.StatsEntity
import github.hua0512.data.stream.entity.StreamDataEntity
import github.hua0512.data.stream.entity.StreamerEntity
import github.hua0512.data.upload.entity.UploadActionEntity
import github.hua0512.data.upload.entity.UploadDataEntity
import github.hua0512.data.upload.entity.UploadResultEntity
import github.hua0512.data.user.UserEntity

/**
 * Room based app database
 * @author hua0512
 * @date : 2024/5/15 21:53
 */
@Database(
  entities = [
    AppConfigEntity::class,
    UserEntity::class,
    StatsEntity::class,
    StreamerEntity::class,
    StreamDataEntity::class,
    UploadActionEntity::class,
    UploadDataEntity::class,
    UploadResultEntity::class
  ], version = DATABASE_VERSION,
  exportSchema = true,
  autoMigrations = [
    AutoMigration(from = 1, to = 2, spec = Migrate1To2::class),
    AutoMigration(from = 2, to = 3)
  ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

  companion object {
    const val DATABASE_VERSION = 3
  }

  abstract fun getConfigDao(): AppConfigDao

  abstract fun getUserDao(): UserDao

  abstract fun getStatsDao(): StatsDao

  abstract fun getStreamerDao(): StreamerDao

  abstract fun getStreamDataDao(): StreamDataDao

  abstract fun getUploadActionDao(): UploadActionDao

  abstract fun getUploadDataDao(): UploadDataDao

  abstract fun getUploadResultDao(): UploadResultDao
}