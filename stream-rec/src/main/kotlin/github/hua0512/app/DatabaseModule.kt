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

package github.hua0512.app

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import github.hua0512.dao.AppDatabase
import github.hua0512.dao.config.AppConfigDao
import github.hua0512.dao.stats.StatsDao
import github.hua0512.dao.stream.StreamDataDao
import github.hua0512.dao.stream.StreamerDao
import github.hua0512.dao.upload.UploadActionDao
import github.hua0512.dao.upload.UploadDataDao
import github.hua0512.dao.upload.UploadResultDao
import github.hua0512.dao.user.UserDao
import github.hua0512.logger
import github.hua0512.repo.LocalDataSource
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString

/**
 * @author hua0512
 * @date : 2024/2/19 11:40
 */
@Module
class DatabaseModule {

  private var firstRun: Boolean = true

  @Provides
  @Singleton
  fun provideRoomDatabase(): AppDatabase {
    val path = Path(LocalDataSource.getDefaultPath()).also {
      it.createParentDirectories()
      logger.info("Database path: ${it.pathString}")
    }
    firstRun = LocalDataSource.isFirstRun()

    // TODO : Remove deprecated sqldelight in next release
    if (!firstRun) {
      // if not first run, check if type file exists
      val dbType = LocalDataSource.getDbType()
      // if db type is not room, migrate from sqldelight to room
      if (dbType != "room") {
        logger.info("Database type is $dbType, migrating to room")
        // copy a bak file
        val bakPath = Path("${path.pathString}.bak")
        path.copyTo(bakPath, overwrite = true)
        // remove sqldelight db
        path.deleteFile()
      }
    } else {
      LocalDataSource.writeDbVersion(AppDatabase.DATABASE_VERSION)
      LocalDataSource.writeDbType("room")
    }

    val builder = Room.databaseBuilder<AppDatabase>(
      name = path.pathString
    )

    return builder
      .fallbackToDestructiveMigration(false)
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(Dispatchers.IO)
      .build()
  }

  @Provides
  fun provideUserDao(database: AppDatabase): UserDao = database.getUserDao()

  @Provides
  fun provideAppConfigDao(database: AppDatabase): AppConfigDao = database.getConfigDao()

  @Provides
  fun provideStreamerDao(database: AppDatabase): StreamerDao = database.getStreamerDao()

  @Provides
  fun provideStreamDataDao(database: AppDatabase): StreamDataDao = database.getStreamDataDao()

  @Provides
  fun provideUploadActionDao(database: AppDatabase): UploadActionDao = database.getUploadActionDao()

  @Provides
  fun provideUploadDataDao(database: AppDatabase): UploadDataDao = database.getUploadDataDao()

  @Provides
  fun provideUploadResultDao(database: AppDatabase): UploadResultDao = database.getUploadResultDao()

  @Provides
  fun provideStatsDao(database: AppDatabase): StatsDao = database.getStatsDao()
}