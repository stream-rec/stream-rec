package github.hua0512.plugins.upload

import github.hua0512.data.dto.IOutputFile
import github.hua0512.data.plugin.UploadError
import github.hua0512.plugins.action.ProcessingPlugin

/**
 * Interface for plugins that upload files to remote services.
 */
interface UploadPlugin : ProcessingPlugin<IOutputFile, IOutputFile, UploadError> {
  /**
   * The type of the upload service (e.g., "ftp", "s3", "http", etc.)
   */
  val uploadServiceType: String

  /**
   * Test connectivity to the upload service.
   *
   * @return True if the connection was successful, false otherwise.
   */
  suspend fun testConnection(): Boolean

  /**
   * Authenticate with the upload service. This may be needed before uploading files.
   * The implementation depends on the specific upload service.
   *
   * @return True if authentication was successful, false otherwise.
   */
  suspend fun authenticate(): Boolean
}
