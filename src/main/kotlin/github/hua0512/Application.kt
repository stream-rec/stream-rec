package github.hua0512

import github.hua0512.app.AppComponent
import github.hua0512.app.DaggerAppComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("Main")


@OptIn(ExperimentalCoroutinesApi::class)
fun main(args: Array<String>) {
  runBlocking {
    DebugProbes.install()
    val appComponent: AppComponent = DaggerAppComponent.create()
    val appConfig = appComponent.getAppConfig()

    val result = appConfig.initConfig()
    if (!result) {
      logger.error("Failed to initialize config: {}", result)
      return@runBlocking
    }
    val downloadService = appComponent.getDownloadService()
    val uploadService = appComponent.getUploadService()

    launch {
      logger.debug("Launching download service in coroutine scope: {}", coroutineContext)
      downloadService.run()
    }
    launch(Dispatchers.IO) {
      uploadService.run()
    }

    Runtime.getRuntime().addShutdownHook(Thread {
      logger.info("Shutting down")
      cancel("Application is shutting down")
      logger.info("Shutdown complete")
    })
  }
}


