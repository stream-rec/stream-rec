package github.hua0512.plugins.action.base

/**
 * Core interface for all plugins in the system.
 */
interface Plugin {
  /**
   * Unique identifier for the plugin.
   */
  val id: String

  /**
   * Display name of the plugin.
   */
  val name: String

  /**
   * Description of the plugin functionality.
   */
  val description: String

  /**
   * Version of the plugin.
   */
  val version: String

  /**
   * Author of the plugin.
   */
  val author: String

  /**
   * Whether the plugin is currently enabled.
   */
  var enabled: Boolean
}
