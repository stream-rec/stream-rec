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

import app.cash.sqldelight.db.SqlDriver
import dagger.Component
import github.hua0512.repo.AppConfigRepository
import github.hua0512.repo.stats.SummaryStatsRepo
import github.hua0512.repo.streamer.StreamDataRepo
import github.hua0512.repo.streamer.StreamerRepo
import github.hua0512.services.DownloadService
import github.hua0512.services.UploadService
import javax.inject.Singleton

@Singleton
@Component(
  modules = [AppModule::class, DatabaseModule::class, RepositoryModule::class]
)
interface AppComponent {

  fun getSqlDriver(): SqlDriver

  fun getAppConfig(): App

  fun getDownloadService(): DownloadService

  fun getUploadService(): UploadService

  fun getAppConfigRepository(): AppConfigRepository

  fun getStatsRepository(): SummaryStatsRepo

  fun getStreamerRepo(): StreamerRepo

  fun getStreamDataRepo(): StreamDataRepo
}