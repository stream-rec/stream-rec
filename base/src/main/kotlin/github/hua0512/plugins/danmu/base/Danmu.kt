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

@file:OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)

package github.hua0512.plugins.danmu.base

import github.hua0512.app.App
import github.hua0512.data.media.ClientDanmuData
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.media.DanmuDataWrapper.DanmuData
import github.hua0512.data.media.DanmuDataWrapper.EndOfDanmu
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.exceptions.DownloadProcessFinishedException
import github.hua0512.utils.withIORetry
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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


  companion object {
    @JvmStatic
    protected val logger: Logger = LoggerFactory.getLogger(Danmu::class.java)

    private const val XML_START = """<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="#s"?>
<i>
"""
    private const val XML_END = "</i>"

    private const val BUFFER_SIZE = 20
  }

  /**
   * Whether the danmu is initialized
   */
  val isInitialized = AtomicBoolean(false)

  /**
   * Danmu websocket url
   */
  abstract val websocketUrl: String

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
  var filePath: String = "${Clock.System.now().toEpochMilliseconds()}.xml"
    set(value) {
      fos = File(value).outputStream().buffered()
      // there is no need to check file existence
      fos.writeStartXml()
      logger.debug("$value wrote XML_START")
      field = value
    }

  /**
   * Danmu file outputstream
   */
  private lateinit var fos: OutputStream

  /**
   * Whether to enable writing danmu to file
   */
  var enableWrite: Boolean = false

  /**
   * Represents the start time of the danmu download.
   */
  var videoStartTime: Instant = Clock.System.now()

  /**
   * Request headers
   */
  protected val headersMap = mutableMapOf<String, String>()

  /**
   * Request parameters
   */
  protected val requestParams = mutableMapOf<String, String>()


  /**
   * Write lock
   */
  private val writeLock: ReentrantLock = ReentrantLock()

  /**
   * Danmu buffer
   */
  private val buffer = mutableListOf<ClientDanmuData>()

  /**
   * Buffer lock
   */
  private val bufferLock = ReentrantLock()

  /**
   * Whether end of danmu is received
   */
  private var hasReceivedEnd = false

  /**
   * Initialize danmu
   *
   * @param streamer streamer
   * @param startTime start time
   * @return true if initialized successfully
   */
  suspend fun init(streamer: Streamer, startTime: Instant = Clock.System.now()): Boolean {
    this.videoStartTime = startTime
    require(websocketUrl.isNotEmpty())
    return initDanmu(streamer, startTime).also { isEnabled ->
      isInitialized.set(isEnabled)
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
   * Callback triggered when danmu fails to connect
   */
  protected abstract fun onDanmuRetry(retryCount: Int)

  /**
   * Fetch danmu from server using websocket
   *
   */
  suspend fun fetchDanmu() = supervisorScope {
    if (!isInitialized.get()) {
      logger.error("danmu is not initialized")
      return@supervisorScope
    }

    // start Websocket with backoff strategy
    withIORetry(
      maxRetries = 10,
      initialDelayMillis = 10000,
      maxDelayMillis = 60000,
      factor = 1.5,
      onError = { e, retryCount ->
        onDanmuRetry(retryCount)
        logger.error("Error connecting ws, $filePath, retry count: $retryCount", e)
      }
    ) {
      logger.debug("Connecting to danmu server: $websocketUrl")
      // determine if ping is enabled
      if (enablePing) {
        app.client.webSocket(websocketUrl, request = {
          fillRequest()
        }) {
          pingIntervalMillis = heartBeatDelay
          processSession()
        }
      } else {
        val urlBuilder = URLBuilder(websocketUrl)
        if (urlBuilder.protocol.isSecure()) {
          app.client.wss(host = urlBuilder.host, port = urlBuilder.port, path = urlBuilder.encodedPath, request = {
            fillRequest()
          }) {
            processSession()
          }
        } else {
          app.client.ws(host = urlBuilder.host, port = urlBuilder.port, path = urlBuilder.encodedPath, request = {
            fillRequest()
          }) {
            processSession()
          }
        }
      }
      // check if coroutine is cancelled
      if (isActive && !hasReceivedEnd) {
        // trigger backoff strategy
        throw IOException("$websocketUrl connection finished")
      } else if (isActive && hasReceivedEnd) {
        throw CancellationException("End of danmu received")
      }
    }

    // wait for cancellation
    awaitCancellation()
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
    // receive incoming
    incoming.receiveAsFlow()
      .flatMapConcat { frame ->
        decodeDanmu(frame.data)
      }
      .flowOn(Dispatchers.Default)
      .buffer()
      .onEach {
        addToBuffer(it)
      }
      .catch {
        if (!(it is EOFException && hasReceivedEnd)) throw it
      }
      .onCompletion {
        it ?: return@onCompletion
        // write end section only when download is aborted or cancelled
        if (it.cause !is DownloadProcessFinishedException) {
          logger.error("$filePath danmu completed: $it")
          if (File(filePath).exists()) {
            writeFinalToFos()
          }
        }
      }
      .flowOn(Dispatchers.IO)
      .collect()
  }


  private fun WebSocketSession.decodeDanmu(data: ByteArray): Flow<ClientDanmuData> = flow {
    try {
      // decode danmu
      for (danmu in decodeDanmu(this@decodeDanmu, data)) {
        when (danmu) {
          is DanmuData -> {
            // calculate delta time
            val delta = danmu.calculateDelta()
            // emit danmu to write to file
            emit(ClientDanmuData(danmu, videoStartTime, delta))
          }

          is EndOfDanmu -> {
            logger.info("$filePath End of danmu received")
            hasReceivedEnd = true
            close()
          }

          else -> logger.error("Unsupported danmu data: {}", danmu)
        }
      }
    } catch (e: Exception) {
      if (e !is CancellationException) {
        logger.error("Error decoding danmu", e)
      }
    }
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
   * Adds the given [ClientDanmuData] object to the buffer and writes the buffer to the output stream if it is full.
   * @param data The [ClientDanmuData] object to be added to the buffer.
   */
  private fun addToBuffer(data: ClientDanmuData) {
    bufferLock.withLock {
      buffer.add(data)
      if (buffer.size >= BUFFER_SIZE) {
        fos.writeToDanmu(buffer)
        buffer.clear()
      }
    }
  }

  /**
   * Writes the given [DanmuData] object to the given [OutputStream].
   *
   * @param batch The [DanmuData] object to be written.
   */
  @Synchronized
  private fun OutputStream.writeToDanmu(batch: Collection<ClientDanmuData>) {
    if (!enableWrite) return
    val xmlContent = buildString {
      for (data in batch) {
        val danmu = data.danmu as DanmuData
        val time = data.clientTime
        val color = if (danmu.color == -1) "16777215" else danmu.color
        val content = danmu.content.replaceToXmlFriendly()
        val sender = danmu.sender.replaceToXmlFriendly()
        // append tab
        append("\t")
        // append danmu content
        append("""<d p="$time,1,25,$color,${danmu.serverTime},0,${danmu.uid},0" user="$sender">${content}</d>""")
        // append newline
        append("\n")
      }
    }
    write(xmlContent.toByteArray())
    flush()
  }

  /**
   * Replaces special characters in the given string with their XML-friendly counterparts.
   * @receiver String the string to be replaced
   * @return String the XML-friendly string
   */
  private fun String.replaceToXmlFriendly(): String = this.replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
    .replace("\n", "&#10;")
    .replace("\r", "&#13;")

  /**
   * Launches a coroutine to send heartbeats on the given WebSocket session.
   */
  protected open fun launchHeartBeatJob(session: WebSocketSession) {
    with(session) {
      launch {
        // repeat a task each heartbeat delay
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
   * Finish current danmu write
   */
  @Synchronized
  fun finish() {
    val exists = File(filePath).exists()
    if (!exists) return
    writeFinalToFos()
  }


  private fun writeFinalToFos() {
    if (!enableWrite) return
    writeLock.withLock {
      try {
        writeRemainingDanmu()
        fos.writeEndXml()
        fos.flush()
      } catch (e: Exception) {
        logger.error("$filePath Error writing final danmu", e)
      } finally {
        enableWrite = false
        try {
          fos.close()
        } catch (e: Exception) {
          logger.error("$filePath Error closing fos", e)
        }
      }
    }
  }

  /**
   * Write remaining danmu to file
   */
  private fun writeRemainingDanmu() {
    bufferLock.withLock {
      if (buffer.isNotEmpty()) {
        fos.writeToDanmu(buffer)
        buffer.clear()
      }
    }
  }

  /**
   * Clean up danmu resources
   */
  open fun clean() {
    enableWrite = false
    hasReceivedEnd = false
    // reset replay cache
    headersMap.clear()
    requestParams.clear()
    buffer.clear()
  }

  /**
   * Write start of XML to output stream
   * @receiver OutputStream output stream
   * @see [XML_START]
   */
  private fun OutputStream.writeStartXml() = writeLock.withLock {
    write(XML_START.toByteArray())
  }


  /**
   * Write end of XML to output stream
   * @receiver OutputStream output stream
   * @see [XML_END]
   */
  private fun OutputStream.writeEndXml() {
    writeLock.withLock {
      write(XML_END.toByteArray())
      flush()
      logger.debug("$filePath wrote XML_END")
    }
  }


  private fun DanmuData.calculateDelta(): Double {
    // danmu server time
    val serverTime = serverTime
    // danmu process start time
    val danmuStartTime = videoStartTime.toEpochMilliseconds()
    // danmu in video time
    val delta = (serverTime - danmuStartTime).run {
      val time = if (this < 0) 0 else this
      String.format("%.3f", time / 1000.0).toDouble()
    }
    return delta
  }

}