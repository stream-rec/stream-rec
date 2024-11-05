package github.hua0512

import github.hua0512.app.App
import github.hua0512.app.AppComponent
import github.hua0512.backend.backendServer
import github.hua0512.dao.AppDatabase
import github.hua0512.dao.ApplicationScope
import github.hua0512.data.config.AppConfig
import github.hua0512.plugins.event.EventCenter
import github.hua0512.repo.AppConfigRepo
import github.hua0512.utils.logger
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class Initializer @Inject constructor(
  @ApplicationScope private val applicationScope: CoroutineScope,
) {

  companion object {
    private val logger = logger(Initializer::class.java)

    /**
     * Backend server instance
     */
    private var server: EmbeddedServer<ApplicationEngine, NettyApplicationEngine.Configuration>? = null
  }


  private var backgroundJob: Job? = null

  private lateinit var app: App

  private lateinit var db: AppDatabase


  fun load(component: AppComponent) {
    // Initialize the application
    app = component.getAppConfig()
    db = component.getDatabase()
    applicationScope.launch {
      initComponents(component, app)
    }
  }

  private suspend inline fun initComponents(
    appComponent: AppComponent,
    app: App,
  ) {

    val appConfigRepository = appComponent.getAppConfigRepository()
    val downloadService = appComponent.getDownloadService()
    val uploadService = appComponent.getUploadService()

    // await for app config to be loaded
    withContext(Dispatchers.IO) {
      initAppConfig(appConfigRepository, app)
    }

    // launch a job to listen for app config changes
    applicationScope.launch(Dispatchers.IO) {
      appConfigRepository.streamAppConfig()
        .collect {
          app.updateConfig(it)
          // TODO : find a way to update download semaphore dynamically
        }
    }

    // start download
    downloadService.run()
    // start upload service
    uploadService.run()
    // listen for events
    EventCenter.run(applicationScope)

    // start the backend server
    applicationScope.launch {
      server = backendServer(
        json = appComponent.getJson(),
        appComponent.getUserRepo(),
        appComponent.getAppConfigRepository(),
        appComponent.getStreamerRepo(),
        appComponent.getStreamDataRepo(),
        appComponent.getStatsRepository(),
        appComponent.getUploadRepo(),
      ).apply {
        start()
      }
    }
  }


  fun destroy() {
    EventCenter.stop()
    server?.stop(1000, 1000)
    Thread.sleep(1000)
    backgroundJob?.cancel()
    app.releaseAll()
    db.close()
    backgroundJob = null
    server = null
  }


  private suspend fun initAppConfig(repo: AppConfigRepo, app: App): AppConfig {
    return repo.getAppConfig().also {
      app.updateConfig(it)
      // TODO : find a way to update download semaphore dynamically
    }
  }

}