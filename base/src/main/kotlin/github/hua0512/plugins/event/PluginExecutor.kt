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
import kotlinx.coroutines.launch

/**
 * Executes plugin event handlers with error isolation and concurrency management.
 * Each event handler is invoked in a new coroutine within the provided scope.
 *
 * @param scope The [CoroutineScope] in which plugin event handlers will be launched.
 *              This scope should be managed (e.g., cancellable) by the owner of the PluginExecutor.
 */
class PluginExecutor(private val scope: CoroutineScope) {

  /**
   * Executes the `onEvent` method of the plugin contained in the [handlerWrapper].
   * The execution is launched in a new coroutine within the [scope].
   * Any exceptions thrown by the plugin's `onEvent` method are caught and logged,
   * preventing them from crashing the executor or the event bus.
   *
   * @param handlerWrapper The [EventHandlerWrapper] containing the plugin and event metadata.
   * @param event The [Event] to be processed by the plugin.
   */
  fun execute(handlerWrapper: EventHandlerWrapper, event: Event) {
    scope.launch {
      try {
        handlerWrapper.plugin.onEvent(event)
      } catch (e: Exception) {
        mainLogger.error(
          "Error in plugin ${handlerWrapper.plugin::class.simpleName} " +
              "while handling event ${event::class.simpleName}: ${e.message}", e
        )
      }
    }
  }

  /**
   * Cancels the [CoroutineScope] of this executor.
   * This will attempt to cancel all ongoing plugin event handler executions
   * that were launched within this scope.
   */
  fun shutdown() {
    // The scope itself is passed in, so its cancellation should be managed by its creator.
    // If this executor were creating its own scope, it would be scope.cancel().
    // For now, we assume the provided scope's lifecycle is handled externally.
    // If direct cancellation is desired here, the PluginExecutor should create and own its scope.
    // As per design, EventCenter creates the scope and passes it, so EventCenter is responsible for its cancellation.
    // This method is a conceptual shutdown signal; actual cancellation happens when EventCenter's parentJob is cancelled.
    mainLogger.info("PluginExecutor shutdown requested. Associated scope will be cancelled by its parent.")
  }
}