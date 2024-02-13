package github.hua0512.app

import dagger.Module
import dagger.Provides
import github.hua0512.services.DownloadService
import github.hua0512.services.UploadService
import javax.inject.Singleton

@Module
class AppModule {

  @Provides
  @Singleton
  fun provideAppConfig(): App = App()

  @Provides
  fun provideDownloadService(app: App, uploadService: UploadService): DownloadService = DownloadService(app, uploadService)

  @Provides
  @Singleton
  fun provideUploadService(app: App): UploadService = UploadService(app)
}