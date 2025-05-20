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
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * A centralized event bus for dispatching [Event]s to subscribed [BaseEventPlugin]s.
 * This object acts as a facade for the underlying event bus implementation.
 *
 * @author hua0512
 * @date : 2024/3/17 20:19 (Legacy date)
 * @date : 2025/05/20 (New architecture)
 */
object EventCenter : CoroutineScope {

  private var internalEventBus: InternalEventBus? = null
  private var subscriptionManager: SubscriptionManager? = null
  private var pluginExecutor: PluginExecutor? = null
  private var configuration: EventBusConfiguration = EventBusConfiguration()

  @Volatile
  private var parentJob: CompletableJob? = null

  override val coroutineContext: CoroutineContext
    get() = parentJob?.plus(configuration.dispatcher)
      ?: // Fallback if not started, though job should exist
      (SupervisorJob() + configuration.dispatcher + CoroutineName("EventCenterGlobal_Inactive"))


  /**
   * Configures the EventCenter with custom parameters.
   * This must be called before [start] for the parameters to take effect.
   * If called after [start], it will log a warning and have no effect.
   *
   * @param params The [EventBusConfiguration] to apply.
   */
  fun configure(params: EventBusConfiguration) {
    if (parentJob?.isActive == true) {
      mainLogger.warn("EventCenter.configure called after start(). New parameters will not apply to the current instance.")
      return
    }
    this.configuration = params
    mainLogger.info("EventCenter configured with: $params")
  }

  /**
   * Starts the EventCenter, initializing and starting all internal components.
   * If already started, this method will log a warning and do nothing.
   */
  @Synchronized
  fun start() {
    if (parentJob?.isActive == true) {
      mainLogger.warn("EventCenter.start() called but it is already running.")
      return
    }
    mainLogger.info("Starting EventCenter...")

    parentJob = SupervisorJob()
    // Ensure coroutineContext uses the fresh parentJob for its children
    val currentCoroutineContext =
      parentJob!! + configuration.dispatcher + CoroutineName("EventCenterGlobal")

    subscriptionManager = SubscriptionManager()
    mainLogger.debug("SubscriptionManager initialized.")

    // Create a dedicated scope for PluginExecutor, child of the main parentJob
    val pluginExecutorScope =
      CoroutineScope(currentCoroutineContext + configuration.pluginExecutorDispatcher + CoroutineName("PluginExecutor"))
    pluginExecutor = PluginExecutor(pluginExecutorScope)
    mainLogger.debug("PluginExecutor initialized.")

    // Create a dedicated scope for InternalEventBus, child of the main parentJob
    val internalEventBusScope = CoroutineScope(currentCoroutineContext + CoroutineName("InternalEventBus"))
    internalEventBus =
      InternalEventBus(internalEventBusScope, configuration, subscriptionManager!!, pluginExecutor!!)
    mainLogger.debug("InternalEventBus initialized.")

    internalEventBus!!.start()
    mainLogger.info("EventCenter started successfully.")
  }

  /**
   * Stops the EventCenter, shutting down all internal components and clearing resources.
   */
  @Synchronized
  fun stop() {
    mainLogger.info("Stopping EventCenter...")
    if (parentJob == null || parentJob?.isActive == false) {
      mainLogger.warn("EventCenter.stop() called but it is not running or already stopped.")
      return
    }

    parentJob?.cancelChildren() // Cancels scopes of InternalEventBus and PluginExecutor
    parentJob?.cancel() // Cancels the parent job itself

    internalEventBus?.stop() // Explicit stop, though scope cancellation should handle it
    pluginExecutor?.shutdown() // Explicit shutdown

    // Clean up plugins before clearing the subscription manager
    val currentManager = subscriptionManager
    if (currentManager != null) {
      mainLogger.info("Cleaning up all subscribed plugins...")
      val pluginsToClean = currentManager.getAllSubscribedPlugins()
      pluginsToClean.forEach(::unsubscribeAll)
      mainLogger.info("Finished cleaning up all subscribed plugins.")
    }

    subscriptionManager?.clear()

    internalEventBus = null
    pluginExecutor = null
    subscriptionManager = null
    parentJob = null

    mainLogger.info("EventCenter stopped successfully.")
  }

  /**
   * Sends an event to the event bus. This is a suspending function.
   * If the EventCenter is not started, this will log an error and return `false`.
   *
   * @param event The [Event] to send.
   * @return `true` if the event was successfully emitted, `false` otherwise.
   */
  suspend fun sendEvent(event: Event): Boolean {
    val bus = internalEventBus
    if (bus == null || parentJob?.isActive != true) {
      mainLogger.error("EventCenter not started. Cannot send event: ${event::class.simpleName}")
      return false
    }
    return bus.emitEvent(event)
  }

  /**
   * Tries to send an event to the event bus without suspension.
   * If the EventCenter is not started, this will log an error and return `false`.
   *
   * @param event The [Event] to send.
   * @return `true` if the event was successfully offered, `false` otherwise (e.g., buffer full or not started).
   */
  fun trySendEvent(event: Event): Boolean {
    val bus = internalEventBus
    if (bus == null || parentJob?.isActive != true) {
      mainLogger.error("EventCenter not started. Cannot trySend event: ${event::class.simpleName}")
      return false
    }
    return bus.tryEmitEvent(event)
  }

  /**
   * Sends a list of events sequentially. This is a suspending function.
   *
   * @param events The list of [Event]s to send.
   */
  suspend fun sendEvents(events: List<Event>) {
    if (internalEventBus == null || parentJob?.isActive != true) {
      mainLogger.error("EventCenter not started. Cannot send events list.")
      return
    }
    for (event in events) {
      sendEvent(event)
    }
  }

  /**
   * Subscribes a [BaseEventPlugin] to a specific [Event] type.
   *
   * @param eventType The [KClass] of the event to subscribe to.
   * @param plugin The plugin instance that will handle the event.
   */
  fun subscribe(eventType: KClass<out Event>, plugin: BaseEventPlugin) {
    val manager = subscriptionManager
    if (manager == null) {
      mainLogger.error("SubscriptionManager not initialized. Cannot subscribe plugin ${plugin::class.simpleName} to ${eventType.simpleName}. EventCenter might not be started.")
      return
    }
    manager.subscribe(eventType, plugin)
    mainLogger.debug("Plugin ${plugin::class.simpleName} subscribed to event ${eventType.simpleName}")
  }

  /**
   * Unsubscribes a [BaseEventPlugin] from a specific [Event] type.
   *
   * @param eventType The [KClass] of the event to unsubscribe from.
   * @param plugin The plugin instance to unsubscribe.
   */
  fun unsubscribe(eventType: KClass<out Event>, plugin: BaseEventPlugin) {
    val manager = subscriptionManager
    if (manager == null) {
      mainLogger.warn("SubscriptionManager not initialized. Cannot unsubscribe plugin ${plugin::class.simpleName} from ${eventType.simpleName}.")
      return
    }
    manager.unsubscribe(eventType, plugin)
    mainLogger.debug("Plugin ${plugin::class.simpleName} unsubscribed from event ${eventType.simpleName}")
  }

  /**
   * Unsubscribes a [BaseEventPlugin] from all events it was subscribed to.
   *
   * @param plugin The plugin instance to unsubscribe.
   */
  fun unsubscribeAll(plugin: BaseEventPlugin) {
    val manager = subscriptionManager
    if (manager == null) {
      mainLogger.warn("SubscriptionManager not initialized. Cannot unsubscribe all for plugin ${plugin::class.simpleName}.")
      return
    }
    manager.unsubscribeAll(plugin)
    // Call cleanUp for the specific plugin after it has been unsubscribed from all events
    try {
      mainLogger.debug("Cleaning up plugin after unsubscribeAll: ${plugin::class.simpleName}")
      plugin.cleanUp()
    } catch (e: Exception) {
      mainLogger.error(
        "Error during cleanUp for plugin ${plugin::class.simpleName} after unsubscribeAll: ${e.message}",
        e
      )
    }
    mainLogger.debug("Plugin ${plugin::class.simpleName} unsubscribed from all events and cleaned up.")
  }
}