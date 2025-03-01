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

package github.hua0512.plugins.event

import github.hua0512.data.event.DownloadEvent.DownloadStateUpdate
import github.hua0512.data.event.Event
import github.hua0512.data.event.StreamerEvent
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Plugin that manages WebSocket connections and distributes download and streamer events
 * @author hua0512
 * @date : 3/1/2025 9:42 PM
 */
class DownloadStateEventPlugin(private val json: Json) : BaseEventPlugin() {

  override val subscribeEvents = listOf(
    DownloadStateUpdate::class.java,
    StreamerEvent::class.java
  )

  // WebSocket Session management
  private val activeConnections = ConcurrentHashMap<String, WebSocketSession>()
  private val lastUpdatesByUrl = ConcurrentHashMap<String, MutableMap<String, Long>>()
  private val updateMutex = Mutex()

  /**
   * Register a new WebSocket connection
   * @param sessionId Unique identifier for the session
   * @param session WebSocket session
   */
  fun registerConnection(sessionId: String, session: WebSocketSession) {
    activeConnections[sessionId] = session
    lastUpdatesByUrl[sessionId] = mutableMapOf()
  }

  /**
   * Remove a WebSocket connection
   * @param sessionId Unique identifier for the session
   */
  fun removeConnection(sessionId: String) {
    activeConnections.remove(sessionId)
    lastUpdatesByUrl.remove(sessionId)
  }

  override suspend fun onEvent(event: Event) {
    // Handle events based on type
    when (event) {
      is StreamerEvent -> broadcastStreamerEvent(event)
      is DownloadStateUpdate -> handleDownloadUpdate(event)
      else -> return
    }
  }

  override fun cleanUp() {
    // clear all active connections
    activeConnections.clear()
    lastUpdatesByUrl.clear()
  }

  /**
   * Broadcast streamer events to all connected clients
   */
  private suspend fun broadcastStreamerEvent(event: StreamerEvent) {
    val frameText = json.encodeToString<StreamerEvent>(event)
    sendToAllActiveSessions(Frame.Text(frameText))
  }

  /**
   * Handle download state updates with rate limiting
   */
  private suspend fun handleDownloadUpdate(event: DownloadStateUpdate) {
    val url = event.url
    val now = System.currentTimeMillis()

    // Rate limit updates for each URL to avoid spamming clients
    updateMutex.withLock {
      // Check all active connections
      for ((sessionId, urlUpdates) in lastUpdatesByUrl) {
        val lastUpdate = urlUpdates[url]
        if (lastUpdate == null || now - lastUpdate > 500) {
          // Update the last update time
          urlUpdates[url] = now

          // Send update to this session
          activeConnections[sessionId]?.let { session ->
            if (!session.isActive) {
              // Remove inactive session
              activeConnections.remove(sessionId)
              lastUpdatesByUrl.remove(sessionId)
            } else {
              // Send update to active session
              val frameText = json.encodeToString<DownloadStateUpdate>(event)
              try {
                session.send(Frame.Text(frameText))
              } catch (e: Exception) {
                // Connection likely closed, remove it
                activeConnections.remove(sessionId)
                lastUpdatesByUrl.remove(sessionId)
              }
            }
          }
        }
      }
    }
  }

  /**
   * Send a frame to all active WebSocket sessions
   */
  private suspend fun sendToAllActiveSessions(frame: Frame) {
    // Clean copy of all sessions to avoid concurrent modification
    val sessionsToRemove = mutableListOf<String>()

    // Send to all active sessions
    for ((sessionId, session) in activeConnections) {
      if (!session.isActive) {
        sessionsToRemove.add(sessionId)
        continue
      }

      try {
        session.send(frame)
      } catch (e: Exception) {
        sessionsToRemove.add(sessionId)
      }
    }

    // Clean up inactive sessions
    sessionsToRemove.forEach { sessionId ->
      activeConnections.remove(sessionId)
      lastUpdatesByUrl.remove(sessionId)
    }
  }
}