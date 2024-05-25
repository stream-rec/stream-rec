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

package github.hua0512.plugins.danmu.base

import github.hua0512.app.App
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.media.DanmuDataWrapper.DanmuData
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.exceptions.DownloadProcessFinishedException
import github.hua0512.utils.chunked
import github.hua0512.utils.withIOContext
import github.hua0512.utils.withIORetry
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Danmu (Bullet screen comments) downloader base class
 *
 * Uses websocket to connect to danmu server and fetch danmu.
 * @param app app config
 * @author hua0512
 * @date : 2024/2/9 13:31
 * @property app app config
 * @property enablePing enable ping pong mechanism
 */
abstract class Danmu(val app: App, val enablePing: Boolean = false) {


  sealed class DanmuState {
    data object NotInitialized : DanmuState()
    data object Initialized : DanmuState()
    data class Fetching(val path: String) : DanmuState()
    data class Error(val error: Throwable) : DanmuState()
    data class Closed(val reason: String) : DanmuState()
  }


  companion object {
    @JvmStatic
    protected val logger: Logger = LoggerFactory.getLogger(Danmu::class.java)

    private const val XML_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n"
    private const val XML_END = "</root>"
  }

  /**
   * Whether the danmu is initialized
   */
  val isInitialized = AtomicBoolean(false)

  /**
   * Danmu websocket url
   */
  abstract var websocketUrl: String

  /**
   * Heart beat delay
   */
  abstract val heartBeatDelay: Long

  /**
   * Heart beat pack
   */
  abstract val heartBeatPack: ByteArray

  /**
   * Danmu state flow
   */
  private val _danmuState = MutableStateFlow<DanmuState>(DanmuState.NotInitialized)

  /**
   * Danmu state flow
   */
  val danmuState: StateFlow<DanmuState> = _danmuState.asStateFlow()


  /**
   * Danmu file name
   */
  var filePath: String = "${Clock.System.now().toEpochMilliseconds()}.xml"

  /**
   * Whether to enable writing danmu to file
   */
  var enableWrite: Boolean = false

  /**
   * Represents the start time of the danmu download.
   */
  var videoStartTime: Instant = Clock.System.now()

  /**
   * A shared flow to write danmu data to file
   */
  private lateinit var writeChannel: Channel<DanmuDataWrapper>

  /**
   * IO job to write danmu to file
   */
  private lateinit var ioJob: Job

  /**
   * Request headers
   */
  protected val headersMap = mutableMapOf<String, String>()

  /**
   * Request parameters
   */
  protected val requestParams = mutableMapOf<String, String>()

  /**
   * Initialize danmu
   *
   * @param streamer streamer
   * @param startTime start time
   * @return true if initialized successfully
   */
  suspend fun init(streamer: Streamer, startTime: Instant = Clock.System.now()): Boolean {
    this.videoStartTime = startTime
    writeChannel = Channel(BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    _danmuState.value = DanmuState.NotInitialized
    return initDanmu(streamer, startTime).also { isEnabled ->
      isInitialized.set(isEnabled)
      if (isEnabled) {
        _danmuState.value = DanmuState.Initialized
      }
    }
  }

  protected abstract suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean

  /**
   * Send one hello
   *
   * @return bytearray hello pack
   */
  abstract fun oneHello(): ByteArray

  /**
   * Fetch danmu from server using websocket
   *
   */
  suspend fun fetchDanmu() = withIOContext {
    supervisorScope {
      if (!isInitialized.get()) {
        logger.error("Danmu is not initialized")
        return@supervisorScope
      }
      if (websocketUrl.isEmpty()) return@supervisorScope

      launch {
        _danmuState.onEach {
          when (it) {
            is DanmuState.Error -> {
              logger.error("Danmu error: ${it.error}")
              // close danmu
              _danmuState.value = DanmuState.Closed(it.error.message ?: "Error")
            }

            is DanmuState.Closed -> {
              logger.info("Danmu closed: {}", it.reason)
            }

            is DanmuState.Fetching -> {
              // launch a coroutine to write danmu to file
              ioJob = this@supervisorScope.launchIOTask(it.path)
            }

            is DanmuState.Initialized -> {
              // fetch danmu
              this@supervisorScope.launch(Dispatchers.IO) {
                withIORetry(
                  maxRetries = 10,
                  initialDelayMillis = 10000,
                  maxDelayMillis = 60000,
                  factor = 1.5,
                  onError = { e, retryCount ->
                    logger.error("Error fetching danmu: $filePath, retry count: $retryCount", e)
                    _danmuState.value = DanmuState.Error(e)
                  }
                ) {
                  _danmuState.value = DanmuState.Fetching(filePath)
                  // determine if ping is enabled
                  if (enablePing) {
                    app.client.webSocket(websocketUrl, request = {
                      fillRequest()
                    }) {
                      processSession()
                    }
                  } else {
                    val urlBuilder = URLBuilder(websocketUrl)
                    logger.trace("Connecting to danmu server: {}", urlBuilder)
                    if (urlBuilder.protocol.isSecure()) {
                      app.client.wssRaw(host = urlBuilder.host, port = urlBuilder.port, path = urlBuilder.encodedPath, request = {
                        fillRequest()
                      }) {
                        processSession()
                      }
                    } else {
                      app.client.wsRaw(host = urlBuilder.host, port = urlBuilder.port, request = {
                        fillRequest()
                      }) {
                        processSession()
                      }
                    }
                  }

                }
              }
            }

            is DanmuState.NotInitialized -> {}
          }
        }
          .catch { e ->
            logger.error("Error handling danmu state", e)
          }
          .flowOn(Dispatchers.IO)
          .collect()
      }
    }
  }

  private fun HttpRequestBuilder.fillRequest() {
    requestParams.forEach { (k, v) ->
      parameter(k, v)
    }
    headersMap.forEach { (k, v) ->
      header(k, v)
    }
  }

  /**
   * Process websocket session
   */
  private suspend fun WebSocketSession.processSession() {
    // make an initial hello
    sendHello(this)
    // launch a coroutine to send heart beat
    launchHeartBeatJob(this)
    incoming.receiveAsFlow()
      .onEach { frame ->
        val data = frame.data
        try {
          // decode danmu
          for (danmu in decodeDanmu(this, data)) {
            when (danmu) {
              is DanmuData -> {
                // danmu server time
                val serverTime = danmu.serverTime
                // danmu process start time
                val danmuStartTime = videoStartTime.toEpochMilliseconds()
                // danmu in video time
                val danmuInVideoTime = (serverTime - danmuStartTime).run {
                  val time = if (this < 0) 0 else this
                  String.format("%.3f", time / 1000.0).toDouble()
                }
                // emit danmu to write to file
                writeChannel.trySend(danmu.copy(clientTime = danmuInVideoTime))
              }

              else -> logger.error("Unsupported danmu data: {}", danmu)
            }
          }
        } catch (e: Exception) {
          logger.error("Error decoding danmu", e)
        }
      }
      .flowOn(Dispatchers.Default)
      .collect()
  }


  /**
   * Launches a coroutine to perform an IO task to write danmu to file.
   *
   * @param filePath The path of the file to which the danmu will be written.
   * @receiver The [CoroutineScope] on which the coroutine will be launched.
   */
  private fun CoroutineScope.launchIOTask(filePath: String): Job {
    val danmuFile = File(filePath)

    return writeChannel.receiveAsFlow()
      .buffer()
      .chunked(20)
      .onStart {
        logger.debug("Start writing danmu to file: {}", filePath)
        // check if danmu file exists
        if (!danmuFile.exists()) {
          danmuFile.createNewFile()
          danmuFile.appendText(XML_START)
        }
      }
      .onEach { data ->
        danmuFile.writeToDanmu(data as List<DanmuData>)
      }
      .onCompletion {
        if (danmuFile.exists())
          danmuFile.writeEndOfFile()
      }
      .catch { e ->
        if (e is DownloadProcessFinishedException) return@catch
        logger.error("Error writing danmu to file {}", filePath, e)
      }
      .flowOn(Dispatchers.IO)
      .launchIn(this)
  }


  /**
   * Sends a hello message to the given WebSocket session.
   *
   * @param session The WebSocket session to which the hello message will be sent.
   */
  protected open suspend fun sendHello(session: WebSocketSession) {
    session.send(oneHello())
    logger.trace("$websocketUrl hello sent")
  }

  /**
   * Writes the given [DanmuData] object to the danmu file.
   *
   * @param batch The list of [DanmuData] objects to be written to the danmu file.
   */
  private fun File.writeToDanmu(batch: List<DanmuData>) {
    if (!enableWrite) return
    val xmlContent = buildString {
      for (data in batch) {
        val time = data.clientTime
        val color = if (data.color == -1) "16777215" else data.color
        val content = data.content.run {
          replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .replace("\n", "&#10;")
            .replace("\r", "&#13;")
        }
        append(
          """
                  <d p="${time},1,25,$color,0,0,0,0">${content}</d>
                """.trimIndent()
        )
        append("\n")
      }
    }
    appendText(xmlContent)
  }

  /**
   * Launches a coroutine to send heartbeats on the given WebSocket session.
   */
  protected open fun launchHeartBeatJob(session: WebSocketSession) {
    with(session) {
      launch {
        // repeat a task each hearbeat delay
        while (isActive) {
          // send heart beat with delay
          send(heartBeatPack)
          logger.trace("{} heart beat sent {}", websocketUrl, heartBeatPack)
          delay(heartBeatDelay)
        }
      }
    }
  }


  /**
   * Decodes the given byte array into a DanmuData object.
   *
   * @param data The byte array to be decoded.
   * @return A list of [DanmuData] objects decoded from the given byte array.
   */
  protected abstract suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?>

  /**
   * Finish writting danmu to file
   */
  fun finish() {
    enableWrite = false
    ioJob.cancel()
    _danmuState.value = DanmuState.Closed("Finish triggered")
  }


  /**
   * Relaunch danmu write IO
   */
  fun relaunchIO(path: String) {
    _danmuState.value = DanmuState.Fetching(path)
  }

  /**
   * Clean up danmu resources
   */
  fun clean() {
    enableWrite = false
    writeChannel.close()
    // reset replay cache
    headersMap.clear()
    requestParams.clear()
  }

  private fun File.writeEndOfFile() {
    appendText(XML_END)
    logger.debug("Finish writing danmu to : $absolutePath")
  }

}