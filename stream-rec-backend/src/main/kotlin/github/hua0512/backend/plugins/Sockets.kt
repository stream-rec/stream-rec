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
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
fun Application.configureSockets(json: Json) {
  install(WebSockets) {
    pingPeriod = Duration.ofSeconds(15)
    timeout = Duration.ofSeconds(15)
    maxFrameSize = Long.MAX_VALUE
    masking = false
  }
  routing {
    webSocket("/live/update") {
      try {
        // collect ws response...
        // no need
        launch {
          for (frame in incoming) {
            if (frame is Frame.Text) {
              val text = frame.readText()
              if (text.equals("bye", ignoreCase = true)) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
              }
            }
          }
        }
        // collect events from event center
        EventCenter.events.buffer().filter {
          it is DownloadStateUpdate || it is StreamerEvent.StreamerOnline || it is StreamerEvent.StreamerOffline
        }.flatMapConcat {
          if (it is DownloadStateUpdate) {
            delay(1000)
          }
          flowOf(it)
        }.collect { event ->
          when (event) {
            is DownloadStateUpdate -> {
              send(Frame.Text(json.encodeToString(DownloadStateUpdate.serializer(), event)))
            }

            is StreamerEvent -> {
              send(Frame.Text(json.encodeToString(StreamerEvent.serializer(), event)))
            }

            else -> {
              logger.error("Unknown event: $event")
            }
          }
        }
      } catch (e: ClosedReceiveChannelException) {
        logger.debug("onClose {}", closeReason.await())
      } catch (e: Throwable) {
        logger.debug("onError {}", closeReason.await())
      }
    }
  }
}
