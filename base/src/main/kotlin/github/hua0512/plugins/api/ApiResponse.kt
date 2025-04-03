package github.hua0512.plugins.api

import github.hua0512.data.plugin.PluginError
import java.net.URL

data class ApiResponse(
  val statusCode: Int,
  val body: String,
  val url: URL? = null,
  val id: String? = null,
  val metadata: Map<String, Any> = emptyMap(),
)

data class ApiError(
  override val message: String,
  val statusCode: Int? = null,
  override val cause: Throwable? = null,
  val response: String? = null,
) : PluginError