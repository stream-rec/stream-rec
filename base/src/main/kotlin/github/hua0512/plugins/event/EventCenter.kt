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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
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

  private val eventChannel = Channel<List<Event>>(capacity = Channel.BUFFERED)

  private val subscriptions = ConcurrentHashMap<Class<out Event>, MutableList<BaseEventPlugin>>()

  // Track jobs for proper cleanup
  private var processingJob: Job? = null

  // Batch collection settings
  private const val maxBatchSize = 50  // Maximum events per batch
  private const val batchTimeoutMs = 10L // Max wait time before flushing batch
  private val pendingEvents = ArrayDeque<Event>(maxBatchSize)
  private var batchJob: Job? = null
  private val batchMutex = Mutex()

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

    // Start batch processing
    batchJob = launch {
      while (isActive) {
        delay(batchTimeoutMs)
        flushBatch()
      }
    }

    processingJob = launch {
      // Main event processing loop
      for (eventBatch in eventChannel) {
        for (event in eventBatch) {
          // Direct dispatch to interested plugins
          val interestedPlugins = subscriptions[event.javaClass] ?: continue

          // Process each plugin in parallel if needed
          if (interestedPlugins.size > 1) {
            coroutineScope {
              interestedPlugins.forEach { plugin ->
                launch(Dispatchers.Default) {
                  try {
                    plugin.onEvent(event)
                  } catch (e: Exception) {
                    // Log but don't crash the event processor
                    println("Error in plugin ${plugin::class.simpleName}: ${e.message}")
                  }
                }
              }
            }
          } else if (interestedPlugins.size == 1) {
            // Single plugin case - avoid launch overhead
            try {
              interestedPlugins[0].onEvent(event)
            } catch (e: Exception) {
              println("Error in plugin ${interestedPlugins[0]::class.simpleName}: ${e.message}")
            }
          }
        }
      }
    }
  }

  /**
   * Flushes current batch if not empty
   */
  private suspend fun flushBatch() {
    batchMutex.withLock {
      if (pendingEvents.isNotEmpty()) {
        val batch = ArrayList<Event>(pendingEvents)
        pendingEvents.clear()
        eventChannel.send(batch)
      }
    }
  }

  /**
   * Add event to current batch and flush if batch is full
   */
  private suspend fun addToBatch(event: Event): Boolean {
    return batchMutex.withLock {
      pendingEvents.add(event)
      if (pendingEvents.size >= maxBatchSize) {
        val batch = ArrayList<Event>(pendingEvents)
        pendingEvents.clear()
        try {
          eventChannel.send(batch)
          true
        } catch (e: Exception) {
          false
        }
      } else {
        true
      }
    }
  }

  /**
   * Send event with suspension if channel is full
   */
  suspend fun sendEvent(event: Event): Boolean = addToBatch(event)

  /**
   * Try to send event without suspension
   */
  fun trySendEvent(event: Event): Boolean {
    // Launch in a new coroutine to avoid blocking
    launch {
      addToBatch(event)
    }
    return true
  }

  /**
   * Send multiple events efficiently with batch processing
   */
  suspend fun sendEvents(events: List<Event>) {
    if (events.isEmpty()) return

    // If batch is larger than max size, send directly
    if (events.size >= maxBatchSize) {
      eventChannel.send(events)
      return
    }

    // Otherwise add to existing batch
    batchMutex.withLock {
      // If adding these would overflow, flush first
      if (pendingEvents.size + events.size > maxBatchSize) {
        val batch = ArrayList<Event>(pendingEvents)
        pendingEvents.clear()
        eventChannel.send(batch)
      }

      // Add new events to pending batch
      pendingEvents.addAll(events)

      // Flush if we're at capacity
      if (pendingEvents.size >= maxBatchSize) {
        val batch = ArrayList<Event>(pendingEvents)
        pendingEvents.clear()
        eventChannel.send(batch)
      }
    }
  }

  /**
   * Clean stop with resource release
   */
  fun stop() {
    batchJob?.cancel()
    batchJob = null

    // Flush any remaining events
    launch {
      flushBatch()

      processingJob?.cancel()
      processingJob = null
      eventChannel.close()
      subscriptions.clear()
      EventPluginsHolder.clearPlugins()
    }
  }
}