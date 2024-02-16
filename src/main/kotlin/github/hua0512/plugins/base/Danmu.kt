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

package github.hua0512.plugins.base

import github.hua0512.app.App
import github.hua0512.data.DanmuDataWrapper
import github.hua0512.data.DanmuDataWrapper.DanmuData
import github.hua0512.data.Streamer
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Danmu (Bullet screen comments) downloader base class
 *
 * Uses websocket to connect to danmu server and fetch danmu.
 * @param app app config
 * @author hua0512
 * @date : 2024/2/9 13:31
 */
abstract class Danmu(val app: App) {

  companion object {
    @JvmStatic
    protected val logger: Logger = LoggerFactory.getLogger(Danmu::class.java)
  }

  /**
   * Whether the danmu is initialized
   */
  protected val isInitialized = AtomicBoolean(false)

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
   * Danmu file name
   */
  var filePath: String = "${System.currentTimeMillis()}.xml"
    set(value) {
      field = value
      danmuFile = Path.of(filePath).toFile()
    }


  /**
   * Danmu file, danmu will be written to this file in xml format
   */
  lateinit var danmuFile: File

  /**
   * Whether to enable writing danmu to file
   * @see [danmuFile]
   */
  var enableWrite: Boolean = false

  /**
   * Represents the start time of the danmu download.
   */
  var startTime: Long = System.currentTimeMillis()

  /**
   * A shared flow to write danmu data to file
   */
  private lateinit var writeChannel: Channel<DanmuDataWrapper?>

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
  suspend fun init(streamer: Streamer, startTime: Long): Boolean {
    this.startTime = startTime
    writeChannel = Channel(BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    return initDanmu(streamer, startTime).also { isEnabled ->
      isInitialized.set(isEnabled)
      if (isEnabled) {
        writeChannel.invokeOnClose {
          logger.info("Danmu {} write channel closed", danmuFile.absolutePath, it)
          writeEndOfFile()
        }
      }
    }
  }

  abstract suspend fun initDanmu(streamer: Streamer, startTime: Long): Boolean

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
  suspend fun fetchDanmu() = coroutineScope {
    if (!isInitialized.get()) {
      logger.error("Danmu is not initialized")
      return@coroutineScope
    }
    if (websocketUrl.isEmpty()) return@coroutineScope

    // launch a coroutine to write danmu to file
    ioJob = launchIOTask()

    // fetch danmu
    withContext(Dispatchers.IO) {
      app.client.webSocket(websocketUrl, request = {
        requestParams.forEach { (k, v) ->
          parameter(k, v)
        }
        headersMap.forEach { (k, v) ->
          header(k, v)
        }
      }) {
        // make an initial hello
        sendHello(this)
        // launch a coroutine to send heart beat
        launchHeartBeatJob(this)
        while (true) {
          // received socket frame
          when (val frame = incoming.receive()) {
            is Frame.Binary -> {
              val data = frame.readBytes()
              // decode danmu
              try {
                decodeDanmu(this, data).filterIsInstance<DanmuData>().forEach {
                  // danmu server time
                  val serverTime = it.serverTime
                  // danmu process start time
                  val danmuStartTime = startTime
                  // danmu in video time
                  val danmuInVideoTime = (serverTime - danmuStartTime).run {
                    val time = if (this < 0) 0 else this
                    String.format("%.3f", time / 1000.0).toDouble()
                  }
                  // emit danmu to write to file
                  writeChannel.send(it.copy(clientTime = danmuInVideoTime))
                }
              } catch (e: Exception) {
                logger.error("Error decoding danmu: $e")
              }
            }

            is Frame.Close -> {
              logger.info("Danmu connection closed")
              break
            }
            // ignore other frames
            else -> {}
          }
        }
      }
    }
  }

  /**
   * Launches a coroutine to perform an IO task to write danmu to file.
   *
   * @receiver The [CoroutineScope] on which the coroutine will be launched.
   */
  private fun CoroutineScope.launchIOTask(): Job {
    return launch {
      writeChannel.consumeAsFlow()
        .onStart {
          if (!danmuFile.exists()) {
            danmuFile.createNewFile()
            danmuFile.appendText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n")
          } else {
            logger.info("Danmu {} already exists", danmuFile.absolutePath)
          }
        }
        .onEach {
          when (it) {
            is DanmuData -> writeToDanmu(it)
            else -> logger.error("Invalid danmu data {}", it)
          }
        }
        .catch { e ->
          logger.error("Error writing danmu to file {}", danmuFile.absolutePath, e)
        }
        .flowOn(Dispatchers.IO)
        .onCompletion {
          logger.info("Danmu {} flow completed, ", danmuFile.absolutePath, it)
        }
        .collect()
    }
  }

  protected suspend fun sendHello(session: DefaultClientWebSocketSession) {
    session.send(oneHello())
  }

  /**
   * Writes the given [DanmuData] object to the danmu file.
   *
   * @param data The [DanmuData] object to be written.
   */
  private fun writeToDanmu(data: DanmuData) {
    if (!enableWrite) return
    val time = data.clientTime
    val color = if (data.color == -1) "16777215" else data.color
    // ignore font size
    val fontSize = data.fontSize
    val xmlContent = """
      <d p="${time},1,25,$color,0,0,0,0">${data.content}</d>
    """.trimIndent()
    danmuFile.appendText("  ${xmlContent}\n")
  }

  /**
   * Launches a coroutine to send heartbeats on the given WebSocket session.
   */
  protected open fun launchHeartBeatJob(session: DefaultClientWebSocketSession) {
    with(session) {
      launch {
        while (true) {
          // send heart beat with delay
          send(heartBeatPack)
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
  abstract suspend fun decodeDanmu(session: DefaultClientWebSocketSession, data: ByteArray): List<DanmuDataWrapper?>

  /**
   * Finish writting danmu to file
   */
  fun finish() {
    logger.info("Danmu $filePath finish triggered")
    writeChannel.close()
    // do not cancel io job here, it will be cancelled when fetchDanmu parent coroutine is cancelled
    enableWrite = false
    // reset replay cache
    headersMap.clear()
    requestParams.clear()
  }

  /**
   * Finish IO job
   */
  suspend fun finishIoJob() {
    ioJob.cancel("Finish IO job")
  }

  private fun writeEndOfFile() {
    danmuFile.appendText("</root>")
    logger.info("Finish writing danmu to : ${danmuFile.absolutePath}")
  }

}