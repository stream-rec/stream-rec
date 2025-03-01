/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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

package github.hua0512.backend.plugins

import github.hua0512.backend.logger
import github.hua0512.plugins.event.DownloadStateEventPlugin
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val heartBeatArray = byteArrayOf(0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte())

private fun Frame.Binary.isHeartBeat(): Boolean {
  return data.contentEquals(heartBeatArray)
}

private fun CoroutineScope.createTimeoutJob(session: WebSocketSession): Job {
  return launch {
    delay(55000)
    session.close(CloseReason(CloseReason.Codes.NORMAL, "Client timeout"))
    session.coroutineContext.cancelChildren()
    // cancel the parent job
    throw CancellationException("Client timeout")
  }
}

fun Application.configureSockets(stateEventPlugin: DownloadStateEventPlugin) {

  install(WebSockets) {
    pingPeriod = 30.toDuration(DurationUnit.SECONDS)
    timeout = 45.toDuration(DurationUnit.SECONDS)
    maxFrameSize = Long.MAX_VALUE
    masking = false
  }

  routing {
    webSocketRaw("/live/update") {
      // Generate a unique session ID
      val sessionId = UUID.randomUUID().toString()

      try {
        var timeOutJob = createTimeoutJob(this)

        // Register this connection with the plugin
        stateEventPlugin.registerConnection(sessionId, this)

        // Handle incoming messages
        incoming.receiveAsFlow()
          .onEach { frame ->
            when (frame) {
              is Frame.Text -> {
                val text = frame.readText()
                if (text.equals("bye", ignoreCase = true)) {
                  close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
                  return@onEach
                }
              }

              is Frame.Binary -> {
                // check if it's a heart beat
                if (frame.isHeartBeat()) {
                  timeOutJob.cancel()
                  timeOutJob = createTimeoutJob(this@webSocketRaw)
                  send(Frame.Binary(true, heartBeatArray))
                }
              }

              else -> {
                // do nothing
              }
            }
          }.collect()
      } catch (e: CancellationException) {
        // client timeout
      } catch (e: ClosedSendChannelException) {
        logger.debug("onClose: ", e)
      } catch (e: ClosedReceiveChannelException) {
        logger.debug("onClose: ", e)
      } catch (e: Throwable) {
        logger.debug("onError: ", e)
      } finally {
        stateEventPlugin.removeConnection(sessionId)
      }
    }
  }
}