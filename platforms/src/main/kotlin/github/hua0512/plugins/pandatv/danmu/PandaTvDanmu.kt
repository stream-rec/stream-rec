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

package github.hua0512.plugins.pandatv.danmu

import github.hua0512.app.App
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.media.DanmuDataWrapper.DanmuData
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.pandatv.download.PandaTvExtractor
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * PandaLive danmu client.
 * @author hua0512
 * @date : 2024/5/9 0:26
 */
class PandaTvDanmu(app: App) : Danmu(app, enablePing = true) {

  companion object {
    private val msgRegex = Regex("\"message\":\"(.+?)\"")
    private val senderRegex = Regex("\"nk\":\"(.+?)\"")
    private val createTimeRegex = Regex("\"created_at\":(\\d+)")
    private const val DANMU_URL = "wss://chat-ws.neolive.kr/connection/websocket"
  }

  private var sendId: Int = 1

  override var websocketUrl: String = DANMU_URL

  override val heartBeatDelay: Long = 60000

  override val heartBeatPack: ByteArray = "{\"method\":\"7\", \"id\":\"${sendId++}\"}".toByteArray()

  lateinit var id: String
  lateinit var token: String
  lateinit var userIdx: String

  init {
    headersMap[HttpHeaders.Origin] = PandaTvExtractor.URL
  }

  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    // extract danmu id from streamer url
    id = PandaTvExtractor.URL_REGEX.toRegex().find(streamer.url)?.groupValues?.get(1) ?: return false
    if (!::token.isInitialized || token.isEmpty() || !::userIdx.isInitialized || userIdx.isEmpty()) {
      throw IllegalStateException("pandatv token not initialized")
    }
    return true
  }

  override fun oneHello(): ByteArray = throw NotImplementedError()

  override suspend fun sendHello(session: WebSocketSession) {
    val authPacket = buildJsonObject {
      put("id", sendId++)
      put("params", buildJsonObject {
        put("token", token)
        put("name", "js")
      })
    }

    /**
     * {"method":1,"params":{"channel":"23788715"},"id":2}
     */
    val joinChannelPacket = """
       {"method":1,"params":{"channel":"$userIdx"},"id":${sendId++}}
    """.trimIndent()

    with(session) {
      logger.trace("$id sending: {}", authPacket)
      send(authPacket.toString())
      logger.trace("$id sending: {}", joinChannelPacket)
      send(joinChannelPacket)
    }
  }


  override suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?> {
    val msg = data.decodeToString()
    val supportedTypes = arrayOf("chatter", "manager")
    // check if the message is a danmu message
    if (!supportedTypes.any { msg.contains("\"type\":\"$it\"") }) {
      return emptyList()
    }
    val message = msgRegex.find(msg)?.groupValues?.get(1) ?: return emptyList()
    val sender = senderRegex.find(msg)?.groupValues?.get(1) ?: ""
    val createTime = createTimeRegex.find(msg)?.groupValues?.get(1)?.toLongOrNull() ?: 0
    return listOf(
      DanmuData(
        sender = sender,
        color = 0,
        content = message,
        fontSize = 0,
        serverTime = createTime * 1000
      )
    )
  }
}