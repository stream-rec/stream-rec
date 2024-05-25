package github.hua0512.backend.plugins

import github.hua0512.data.event.DownloadEvent.DownloadStateUpdate
import github.hua0512.data.event.StreamerEvent
import github.hua0512.logger
import github.hua0512.plugins.event.EventCenter
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration


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

        var timeOutJob = createTimeoutJob(this)

        // collect streamer events and send to client
        EventCenter.events.filterIsInstance<StreamerEvent>().onEach {
          // check if this websocket is still open
          if (timeOutJob.isCompleted || !this.isActive) {
            cancel("Client timeout")
            return@onEach
          }
          send(Frame.Text(json.encodeToString<StreamerEvent>(it)))
        }.launchIn(this)


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
        }.onEach {
          // check if this websocket is still open and active
          if (timeOutJob.isCompleted || !this.isActive) {
            cancel("Client timeout")
            return@onEach
          }
          send(Frame.Text(json.encodeToString(DownloadStateUpdate.serializer(), it)))
        }.launchIn(this)

        // collect ws response...
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
      }
    }
  }
}