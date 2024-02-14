package github.hua0512.services

import github.hua0512.app.App
import github.hua0512.data.StreamData
import github.hua0512.data.Streamer
import github.hua0512.data.StreamingPlatform
import github.hua0512.plugins.base.Download
import github.hua0512.plugins.danmu.douyin.DouyinDanmu
import github.hua0512.plugins.danmu.huya.HuyaDanmu
import github.hua0512.plugins.download.Douyin
import github.hua0512.plugins.download.Huya
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author hua0512
 * @date : 2024/2/14 2:42
 */
class NewDownloadService(val app: App, val uploadService: UploadService) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(NewDownloadService::class.java)
  }

  private fun getPlaformDownloader(platform: StreamingPlatform): Download = when (platform) {
    StreamingPlatform.HUYA -> Huya(app, HuyaDanmu(app))
    StreamingPlatform.DOUYIN -> Douyin(app, DouyinDanmu(app))
    else -> throw Exception("Platform not supported")
  }


  suspend fun run() = withContext(Dispatchers.IO) {
    val streamers = app.config.streamers

    streamers.filter {
      !it.isLive && it.isActivated
    }.asFlow()
      .flatMapMerge {
        val downloader = getPlaformDownloader(it.platform)
        downloadStreamer(it, downloader)
      }
      .launchIn(this)

  }

  private fun downloadStreamer(it: Streamer, downloader: Download): Flow<List<StreamData>> = flow {
    var attempts = 0
    var success = false
    while (true) {

      try {
        val data = downloader.download()
        emit(data)
        success = true
        break
      } catch (e: Exception) {
        logger.error("Error downloading streamer ${it.name}", e)
        attempts++
//        if (attempts >= app.config.maxDownloadAttempts) {
//          logger.error("Max download attempts reached for streamer ${it.name}")
//          break
//        }
      }

    }
  }

}