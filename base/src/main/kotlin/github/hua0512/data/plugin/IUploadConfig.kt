package github.hua0512.data.plugin

import github.hua0512.data.upload.UploadPlatform

/**
 * Base configuration for upload plugins.
 */
interface IUploadConfig {

  val platform: UploadPlatform

  /**
   * Timeout in milliseconds for uploads.
   */
  val timeoutMs: Long

  /**
   * Number of retries for failed uploads.
   */
  val retryCount: Int

  /**
   * Delay between retries in milliseconds.
   */
  val retryDelayMs: Long

  /**
   * Filter pattern to determine which files should be uploaded.
   * Format depends on implementation, could be glob, regex, etc.
   */
  val fileFilter: String?

  /**
   * Whether to delete the local file after successful upload.
   */
  val deleteAfterUpload: Boolean

  /**
   * Whether to validate the uploaded file after upload (e.g., size check, hash check).
   */
  val validateAfterUpload: Boolean
}