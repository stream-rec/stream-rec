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
import github.hua0512.dao.config.AppConfigDao
import github.hua0512.dao.user.UserDao
import github.hua0512.repo.LocalDataSource
import github.hua0512.repo.LocalDataSourceImpl
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.repo.upload.UploadRepo
import github.hua0512.services.ActionService
import github.hua0512.services.DownloadService
import github.hua0512.services.UploadService
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
class AppModule {

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
  }

  @Provides
  @Singleton
  fun provideAppConfig(json: Json): App = App(json)

  @Provides
  @Singleton
  fun provideActionService(app: App, uploadService: UploadService): ActionService = ActionService(app, uploadService)

  @Provides
  @Singleton
  fun provideDownloadService(
    app: App,
    actionService: ActionService,
    streamerRepository: StreamerRepo,
    streamDataRepository: StreamDataRepo,
  ): DownloadService =
    DownloadService(app, actionService, streamerRepository, streamDataRepository)

  @Provides
  @Singleton
  fun provideUploadService(app: App, uploadRepo: UploadRepo): UploadService = UploadService(app, uploadRepo)

  @Provides
  fun provideLocalDataSource(appDao: AppConfigDao, userDao: UserDao): LocalDataSource = LocalDataSourceImpl(appDao, userDao)

}