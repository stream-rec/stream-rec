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
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Manages event subscriptions, mapping event types to their respective handlers.
 * It uses [EventHandlerWrapper] to associate plugins with specific event types they subscribe to.
 */
class SubscriptionManager {

  // Maps an event KClass to a set of EventHandlerWrappers that handle this event type.
  private val subscriptions = ConcurrentHashMap<KClass<out Event>, MutableSet<EventHandlerWrapper>>()

  // Maps a plugin instance to all its EventHandlerWrappers, to facilitate unsubscribing a plugin from all events.
  private val pluginToWrappersMap = ConcurrentHashMap<BaseEventPlugin, MutableSet<EventHandlerWrapper>>()

  /**
   * Subscribes a plugin to a specific event type.
   *
   * @param eventType The KClass of the event to subscribe to.
   * @param plugin The [BaseEventPlugin] instance that will handle the event.
   */
  fun subscribe(eventType: KClass<out Event>, plugin: BaseEventPlugin) {
    val wrapper = EventHandlerWrapper(plugin, eventType)
    subscriptions.getOrPut(eventType) { ConcurrentHashMap.newKeySet() }.add(wrapper)
    pluginToWrappersMap.getOrPut(plugin) { ConcurrentHashMap.newKeySet() }.add(wrapper)
  }

  /**
   * Unsubscribes a plugin from a specific event type.
   *
   * @param eventType The KClass of the event to unsubscribe from.
   * @param plugin The [BaseEventPlugin] instance to unsubscribe.
   */
  fun unsubscribe(eventType: KClass<out Event>, plugin: BaseEventPlugin) {
    val wrapperToSearch = EventHandlerWrapper(plugin, eventType)
    subscriptions[eventType]?.remove(wrapperToSearch)
    pluginToWrappersMap[plugin]?.remove(wrapperToSearch)

    // Clean up empty sets
    if (subscriptions[eventType]?.isEmpty() == true) {
      subscriptions.remove(eventType)
    }
    if (pluginToWrappersMap[plugin]?.isEmpty() == true) {
      pluginToWrappersMap.remove(plugin)
    }
  }

  /**
   * Unsubscribes a plugin from all event types it was subscribed to.
   *
   * @param plugin The [BaseEventPlugin] instance to unsubscribe from all events.
   */
  fun unsubscribeAll(plugin: BaseEventPlugin) {
    pluginToWrappersMap.remove(plugin)?.forEach { wrapper ->
      subscriptions[wrapper.subscribedEventType]?.remove(wrapper)
      if (subscriptions[wrapper.subscribedEventType]?.isEmpty() == true) {
        subscriptions.remove(wrapper.subscribedEventType)
      }
    }
  }

  /**
   * Retrieves a list of [EventHandlerWrapper]s for a given event type.
   * This method considers event inheritance, meaning if a plugin is subscribed to a superclass
   * of the [actualEventType], it will be included in the results.
   *
   * @param actualEventType The KClass of the event that was actually emitted.
   * @return A list of [EventHandlerWrapper]s that should handle the event.
   */
  fun getSubscribersFor(actualEventType: KClass<out Event>): List<EventHandlerWrapper> {
    val handlers = mutableListOf<EventHandlerWrapper>()
    subscriptions.forEach { (subscribedEventType, wrappers) ->
      if (subscribedEventType.java.isAssignableFrom(actualEventType.java)) {
        handlers.addAll(wrappers)
      }
    }
    // Ensure a plugin isn't called multiple times for the same event if subscribed via multiple compatible types
    return handlers.distinct()
  }

  /**
   * Retrieves a set of all unique [BaseEventPlugin] instances that are currently subscribed
   * to any event.
   *
   * @return A new [Set] containing all unique subscribed plugins.
   */
  fun getAllSubscribedPlugins(): Set<BaseEventPlugin> {
    return pluginToWrappersMap.keys.toSet()
  }

  /**
   * Clears all subscriptions.
   */
  fun clear() {
    subscriptions.clear()
    pluginToWrappersMap.clear()
  }
}