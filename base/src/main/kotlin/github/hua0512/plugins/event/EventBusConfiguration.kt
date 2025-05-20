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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow

/**
 * Configuration parameters for the EventBus.
 *
 * @property replayCacheSize Replay cache size for the main event SharedFlow. Default is 0.
 * @property extraBufferCapacity Extra buffer capacity for the main event SharedFlow. Default is 64.
 * @property onBufferOverflow Action to take when the event SharedFlow buffer overflows. Default is [BufferOverflow.SUSPEND].
 * @property dispatcher Coroutine dispatcher for the InternalEventBus processing. Default is [Dispatchers.Default].
 * @property pluginExecutorDispatcher Coroutine dispatcher for the PluginExecutor. Default is [Dispatchers.Default].
 * @property logEventsWithNoSubscribers Whether to log a warning when an event is emitted but has no subscribers. Default is true.
 */
data class EventBusConfiguration(
  val replayCacheSize: Int = 0,
  val extraBufferCapacity: Int = 64,
  val onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
  val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  val pluginExecutorDispatcher: CoroutineDispatcher = Dispatchers.IO,
  val logEventsWithNoSubscribers: Boolean = true
)