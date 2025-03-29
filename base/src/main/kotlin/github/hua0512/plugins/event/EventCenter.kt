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

import github.hua0512.data.event.Event
import github.hua0512.utils.mainLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * High-throughput broadcast event center
 * @author hua0512
 * @date : 2024/3/17 20:19
 */
object EventCenter : CoroutineScope {
  // Main processing scope with custom dispatcher optimized for CPU-bound work
  override val coroutineContext: CoroutineContext =
    Dispatchers.Default + SupervisorJob() + CoroutineName("EventCenter")

  private val eventChannel = Channel<Event>(capacity = Channel.BUFFERED)

  private val subscriptions = ConcurrentHashMap<Class<out Event>, MutableList<BaseEventPlugin>>()

  // Track jobs for proper cleanup
  private var processingJob: Job? = null

  /**
   * Start the event center
   */
  fun start() {
    if (processingJob != null) return

    // Build the subscription map for faster lookups
    EventPluginsHolder.getPlugins().forEach { plugin ->
      plugin.subscribeEvents.forEach { eventClass ->
        subscriptions.getOrPut(eventClass) { mutableListOf() }.add(plugin)
      }
    }

    processingJob = launch {
      // Main event processing loop
      for (event in eventChannel) {
        // Find all interested plugins
        val interestedPlugins = subscriptions.entries
          .filter { (eventClass, _) -> eventClass.isAssignableFrom(event.javaClass) }
          .flatMap { it.value }
          .distinct()

        if (interestedPlugins.isEmpty()) continue

        // Process each plugin in parallel if needed
        if (interestedPlugins.size > 1) {
          coroutineScope {
            interestedPlugins.forEach { plugin ->
              launch(Dispatchers.Default) {
                try {
                  plugin.onEvent(event)
                } catch (e: Exception) {
                  // Log but don't crash the event processor
                  mainLogger.error("Error in plugin ${plugin::class.simpleName}: ${e.message}")
                }
              }
            }
          }
        } else {
          // Single plugin case - avoid launch overhead
          try {
            interestedPlugins[0].onEvent(event)
          } catch (e: Exception) {
            mainLogger.error("Error in plugin ${interestedPlugins[0]::class.simpleName}: ${e.message}")
          }
        }
      }
    }
  }

  /**
   * Send event with suspension if channel is full
   */
  suspend fun sendEvent(event: Event): Boolean {
    return try {
      eventChannel.send(event)
      true
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Try to send event without suspension
   */
  fun trySendEvent(event: Event): Boolean {
    // Launch in a new coroutine to avoid blocking
    val result = eventChannel.trySend(event)
    return result.isSuccess
  }

  /**
   * Send multiple events
   */
  suspend fun sendEvents(events: List<Event>) {
    for (event in events) {
      sendEvent(event)
    }
  }

  /**
   * Clean stop with resource release
   */
  fun stop() {
    processingJob?.cancel()
    processingJob = null
    eventChannel.close()
    subscriptions.clear()
    EventPluginsHolder.clearPlugins()
  }
}