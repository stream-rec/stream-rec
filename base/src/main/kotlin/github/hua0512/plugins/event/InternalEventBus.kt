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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Manages the core event flow, event emission, and dispatching to subscribers via [PluginExecutor].
 * This class is intended for internal use by the [EventCenter].
 *
 * @param scope The [CoroutineScope] in which the event processing loop will run.
 * @param params The [EventBusConfiguration] for this event bus.
 * @param subscriptionManager The [SubscriptionManager] to get event subscribers.
 * @param pluginExecutor The [PluginExecutor] to execute plugin event handlers.
 */
class InternalEventBus(
  private val scope: CoroutineScope,
  private val params: EventBusConfiguration,
  private val subscriptionManager: SubscriptionManager,
  private val pluginExecutor: PluginExecutor
) {

  private val eventFlow = MutableSharedFlow<Event>(
    replay = params.replayCacheSize,
    extraBufferCapacity = params.extraBufferCapacity,
    onBufferOverflow = params.onBufferOverflow
  )

  private var processingJob: Job? = null

  /**
   * Starts the event processing loop.
   * This launches a coroutine in the provided [scope] that collects events from the [eventFlow]
   * and dispatches them to subscribed handlers.
   * If the processing job is already running, this method does nothing.
   */
  fun start() {
    if (processingJob?.isActive == true) {
      mainLogger.info("InternalEventBus is already running.")
      return
    }
    processingJob = scope.launch(params.dispatcher) {
      mainLogger.info("InternalEventBus processing loop started.")
      try {
        eventFlow.collect { event ->
          val handlers = subscriptionManager.getSubscribersFor(event::class)
          if (handlers.isEmpty()) {
            if (params.logEventsWithNoSubscribers) {
              mainLogger.warn("No subscribers for event: ${event::class.simpleName} ($event)")
            }
          } else {
            handlers.forEach { handlerWrapper ->
              pluginExecutor.execute(handlerWrapper, event)
            }
          }
        }
      } finally {
        mainLogger.info("InternalEventBus processing loop stopped.")
      }
    }
  }

  /**
   * Stops the event processing loop by cancelling its job.
   */
  fun stop() {
    processingJob?.cancel()
    processingJob = null
    mainLogger.info("InternalEventBus stop requested.")
  }

  /**
   * Emits an event to the event flow. This is a suspending function.
   *
   * @param event The [Event] to emit.
   * @return `true` if the event was emitted successfully, `false` otherwise (e.g., if the channel is closed).
   */
  suspend fun emitEvent(event: Event): Boolean {
    return try {
      eventFlow.emit(event)
      true
    } catch (e: Exception) {
      mainLogger.error("Failed to emit event: ${event::class.simpleName}. Error: ${e.message}", e)
      false
    }
  }

  /**
   * Tries to emit an event to the event flow without suspension.
   *
   * @param event The [Event] to emit.
   * @return `true` if the event was emitted successfully, `false` otherwise (e.g., if buffer is full or channel is closed).
   */
  fun tryEmitEvent(event: Event): Boolean {
    val result = eventFlow.tryEmit(event)
    if (!result && eventFlow.subscriptionCount.value > 0) { // Log only if there are subscribers, as failure might be due to no collectors yet if bus just started
      mainLogger.warn("Failed to tryEmit event: ${event::class.simpleName}. Buffer full or channel closed.")
    }
    return result
  }
}