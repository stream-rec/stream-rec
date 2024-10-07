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

package github.hua0512.plugins.twitch.danmu

import github.hua0512.app.App
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.media.DanmuDataWrapper.DanmuData
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.twitch.download.TwitchExtractor
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Twitch chat comments download implementation
 * @author hua0512
 * @date : 2024/5/4 21:55
 */
class TwitchDanmu(app: App) : Danmu(app = app, enablePing = false) {

  companion object {
    const val WEB_SOCKET_URL = "wss://irc-ws.chat.twitch.tv"
  }

  override var websocketUrl: String = WEB_SOCKET_URL

  /**
   * Heartbeat delay, default 40s
   */
  override val heartBeatDelay: Long = 40000

  override val heartBeatPack: ByteArray = "PONG :tmi.twitch.tv".toByteArray()

  internal lateinit var channel: String


  init {
    headersMap[HttpHeaders.Origin] = TwitchExtractor.BASE_URL
  }

  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    // extract channel from url
    channel = TwitchExtractor.URL_REGEX.toRegex().find(streamer.url)?.groupValues?.get(1) ?: return false
    return true
  }

  override fun oneHello(): ByteArray = throw UnsupportedOperationException("TwitchDanmu does not support oneHello")

  override fun onDanmuRetry(retryCount: Int) {
    // do nothing
  }

  override suspend fun sendHello(session: WebSocketSession) {
    val user = "justinfan${(1000..99999).random()}"
    with(session) {
      send("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
      send("PASS SCHMOOPIIE")
      send("NICK $user")
      send("USER $user 8 * :$user")
      send("JOIN #$channel")
    }
  }

  override fun launchHeartBeatJob(session: WebSocketSession) {
    // twitch heartbeat is manually sent
  }

  private val contentPattern = Regex("PRIVMSG [^:]+:(.+)")
  private val namePattern = Regex("display-name=([^;]+);")
  private val colorPattern = Regex("color=#([a-zA-Z0-9]{6});")
  private val timePattern = Regex("tmi-sent-ts=(\\d+);")
  private val userIdPattern = Regex("user-id=(\\d+);")

  override suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?> {
    val message = data.decodeToString()
    if (message.startsWith("PING")) {
      // respond to PING according to https://dev.twitch.tv/docs/irc/#keepalive-messages
      session.send(message.replaceFirst("PING", "PONG"))
      return emptyList()
    }
    // parse twitch message
    // only handle PRIVMSG
    if (!message.contains("PRIVMSG")) {
      return emptyList()
    }

    val content = contentPattern.find(message)?.groupValues?.get(1) ?: return emptyList()
    val name = namePattern.find(message)?.groupValues?.get(1) ?: ""
    val color = colorPattern.find(message)?.groupValues?.get(1)
    val time = timePattern.find(message)?.groupValues?.get(1) ?: Clock.System.now().toEpochMilliseconds().toString()
    val userId = userIdPattern.find(message)?.groupValues?.get(1)?.toLong() ?: 0L
    return listOf(
      DanmuData(
        uid = userId,
        sender = name,
        color = color?.toInt(16) ?: 0,
        content = content,
        fontSize = 0,
        serverTime = time.toLong(),
      )
    )
  }
}