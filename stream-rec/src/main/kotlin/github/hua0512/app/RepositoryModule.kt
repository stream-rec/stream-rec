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

import dagger.Module
import dagger.Provides
import github.hua0512.dao.config.EngineConfigDao
import github.hua0512.dao.stats.StatsDao
import github.hua0512.dao.stream.StreamDataDao
import github.hua0512.dao.stream.StreamerDao
import github.hua0512.dao.upload.UploadActionDao
import github.hua0512.dao.upload.UploadDataDao
import github.hua0512.dao.upload.UploadResultDao
import github.hua0512.dao.user.UserDao
import github.hua0512.repo.*
import github.hua0512.repo.config.EngineConfigManager
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.stats.SummaryStatsRepoImpl
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.repo.upload.UploadRepo
import kotlinx.serialization.json.Json

/**
 * @author hua0512
 * @date : 2024/2/19 11:49
 */
@Module
class RepositoryModule {

  @Provides
  fun provideUserRepository(userDao: UserDao): UserRepo = UserRepository(userDao)

  @Provides
  fun provideAppConfigRepository(
    localDataSource: LocalDataSource,
  ): AppConfigRepo =
    AppConfigRepository(localDataSource)

  @Provides
  fun provideStreamerRepository(streamerDao: StreamerDao): StreamerRepo = StreamerRepository(streamerDao)

  @Provides
  fun provideStreamDataRepository(streamDataDao: StreamDataDao, statsDao: StatsDao): StreamDataRepo =
    StreamDataRepository(streamDataDao, statsDao)

  @Provides
  fun provideUploadActionRepository(
    streamerRepo: StreamerRepo,
    streamsRepo: StreamDataRepo,
    uploadActionDao: UploadActionDao,
    uploadDataDao: UploadDataDao,
    uploadResultDao: UploadResultDao,
    statsDao: StatsDao,
  ): UploadRepo =
    UploadActionRepository(streamerRepo, streamsRepo, uploadActionDao, uploadDataDao, uploadResultDao, statsDao)

  @Provides
  fun provideStatsRepository(statsDao: StatsDao): SummaryStatsRepo = SummaryStatsRepoImpl(statsDao)

  @Provides
  fun provideEngineConfigManager(
    engineConfigDao: EngineConfigDao,
    json: Json,
  ): EngineConfigManager = EngineConfigManager(engineConfigDao, json)
}