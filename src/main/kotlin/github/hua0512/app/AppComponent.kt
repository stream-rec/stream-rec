package github.hua0512.app

import dagger.Component
import github.hua0512.services.DownloadService
import github.hua0512.services.UploadService
import javax.inject.Singleton

@Singleton
@Component(
  modules = [AppModule::class]
)
interface AppComponent {


  fun getAppConfig(): App

  fun getDownloadService(): DownloadService

  fun getUploadService(): UploadService
}