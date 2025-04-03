package github.hua0512.data.plugin

import java.net.URL

/**
 * Represents the result of an upload operation.
 */
data class UploadPluginResult(
  /**
   * Whether the upload was successful.
   */
  val success: Boolean,

  /**
   * URL to access the uploaded file, if available.
   */
  val url: URL? = null,

  /**
   * Identifier or path to the uploaded file on the remote system.
   */
  val remoteId: String? = null,

  /**
   * Size of the uploaded file in bytes.
   */
  val size: Long? = null,

  /**
   * Additional metadata about the upload as key-value pairs.
   */
  val metadata: Map<String, Any> = emptyMap(),

  /**
   * Time taken for the upload in milliseconds.
   */
  val uploadTimeMs: Long? = null,
)