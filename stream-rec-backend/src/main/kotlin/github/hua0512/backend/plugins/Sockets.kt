@file:OptIn(FlowPreview::class)

package github.hua0512.backend.plugins

import github.hua0512.data.event.DownloadEvent.DownloadStateUpdate
import github.hua0512.data.event.StreamerEvent
import github.hua0512.logger
import github.hua0512.plugins.event.EventCenter
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration


val heartBeatArray = byteArrayOf(0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte())


@OptIn(ExperimentalCoroutinesApi::class)
fun Application.configureSockets(json: Json) {
  install(WebSockets) {
    pingPeriod = Duration.ofSeconds(30)
    timeout = Duration.ofSeconds(45)
    maxFrameSize = Long.MAX_VALUE
    masking = false
  }
  routing {
    webSocketRaw("/live/update") {
      try {
        var timeOutJob = launch {
          delay(55000)
          close(CloseReason(CloseReason.Codes.NORMAL, "Client timeout"))
        }
        // collect ws response...
        launch {
          for (frame in incoming) {
            if (frame is Frame.Text) {
              val text = frame.readText()
              if (text.equals("bye", ignoreCase = true)) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
              }
            } else if (frame is Frame.Binary) {
              // check if it's a heart beat
              if (frame.data.contentEquals(heartBeatArray)) {
                timeOutJob.cancel()
                timeOutJob = launch {
                  delay(55000)
                  close(CloseReason(CloseReason.Codes.NORMAL, "Client timeout"))
                }
                send(Frame.Binary(true, heartBeatArray))
              }
            }
          }
        }

        launch {
          EventCenter.events.filter {
            it is StreamerEvent
          }.collect { event ->
            send(Frame.Text(json.encodeToString(StreamerEvent.serializer(), event as StreamerEvent)))
          }
        }
        // collect events from event center
        // if a same event from a same url is received within 1 second, ignore it
        // otherwise, send it to client
        val lastUpdate = mutableMapOf<String, Long>()
        EventCenter.events.filter {
          it is DownloadStateUpdate
        }.flatMapConcat { update ->
          val event = update as DownloadStateUpdate
          val url = event.url
          val last = lastUpdate[url]
          val now = System.currentTimeMillis()
          if (last == null || now - last > 1000) {
            lastUpdate[url] = now
            flowOf(event)
          } else {
            emptyFlow()
          }
        }.collect { event ->
          // check if this websocket is still open
          send(Frame.Text(json.encodeToString(DownloadStateUpdate.serializer(), event)))
        }
        lastUpdate.clear()
      } catch (e: ClosedReceiveChannelException) {
        logger.debug("onClose: ", e)
      } catch (e: Throwable) {
        logger.debug("onError: ", e)
      }
    }
  }
}
