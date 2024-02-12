package github.hua0512.plugins.download.engines

import github.hua0512.app.App
import github.hua0512.data.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author hua0512
 * @date : 2024/2/12 18:22
 */
abstract class BaseDownloadEngine(open val app: App) {

  protected var cookies: String? = ""
  protected var downloadUrl: String? = null
  protected var downloadFilePath: String = ""
  protected var headers = mutableMapOf<String, String>()
  protected var streamData: StreamData? = null
  protected var startTime: Long = 0
  protected var fileLimitSize: Long = 0
  protected var isInitialized = false

  fun init(
    downloadUrl: String,
    downloadFilePath: String,
    streamData: StreamData,
    cookies: String? = "",
    headers: Map<String, String>,
    startTime: Long = System.currentTimeMillis(),
    fileLimitSize: Long = 0,
  ) {
    this.downloadUrl = downloadUrl
    this.downloadFilePath = downloadFilePath
    this.streamData = streamData
    this.cookies = cookies
    this.headers = headers.toMutableMap()
    this.startTime = startTime
    this.fileLimitSize = fileLimitSize
    isInitialized = true
  }

  open suspend fun run(): StreamData? {
    if (!isInitialized) {
      throw IllegalStateException("Engine is not initialized")
    }
    return withContext(Dispatchers.IO) {
      startDownload()
    }
  }

  abstract suspend fun startDownload(): StreamData?

}