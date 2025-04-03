package github.hua0512.plugins.upload

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.plugin.*
import github.hua0512.data.plugin.PluginConfigs.UploadConfig.ApiUploadConfig
import github.hua0512.data.dto.IOutputFile
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.upload.UploadRepo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.URL


/**
 * Upload plugin that uses HTTP APIs to perform uploads.
 */
class ApiUploadPlugin(
  config: ApiUploadConfig,
  val httpClient: HttpClient,
  uploadSemaphore: Semaphore,
  streamRepo: StreamDataRepo,
  uploadRepository: UploadRepo,
) : BaseUploadPlugin<ApiUploadConfig>(config, uploadSemaphore, streamRepo, uploadRepository) {


  override val id: String = "api-upload"
  override val name: String = "API Upload"
  override val description: String = "Uploads files using HTTP APIs"
  override val version: String = "1.0.0"
  override val author: String = "System"
  override val uploadServiceType: String = "api"

  // Token storage for OAuth authentication
  private var oauthToken: String? = null
  private var tokenExpirationTime: Long = 0

  override suspend fun testConnection(): Boolean {
    return try {
      val response = httpClient.get("${config.baseUrl}/ping") {
        timeout {
          requestTimeoutMillis = 10000 // Short timeout for ping
        }
      }
      response.status.isSuccess()
    } catch (e: Exception) {
      false
    }
  }

  override suspend fun authenticate(): Boolean {
    val auth = config.auth ?: return true // No auth configured means success

    // Basic and API key auth handled in client configuration
    if (auth is ApiAuth.Basic || auth is ApiAuth.ApiKey || auth is ApiAuth.Bearer) {
      return true
    }

    // Handle OAuth authentication
    if (auth is ApiAuth.OAuth) {
      // Check if we have a valid token
      if (oauthToken != null && System.currentTimeMillis() < tokenExpirationTime) {
        return true
      }

      return try {
        val response = httpClient.submitForm(
          url = auth.tokenUrl,
          formParameters = Parameters.build {
            append("grant_type", "client_credentials")
            append("client_id", auth.clientId)
            append("client_secret", auth.clientSecret)
            if (!auth.scope.isNullOrBlank()) {
              append("scope", auth.scope)
            }
          }
        )

        if (response.status.isSuccess()) {
          val body = response.body<JsonObject>()
          oauthToken = body["access_token"]?.jsonPrimitive?.content

          // Calculate expiration time
          val expiresIn = body["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600
          tokenExpirationTime = System.currentTimeMillis() + (expiresIn * 1000L)

          true
        } else {
          false
        }
      } catch (e: Exception) {
        false
      }
    }

    return true
  }

  override suspend fun performUpload(file: IOutputFile): Result<UploadPluginResult, UploadError> =
    withContext(Dispatchers.IO) {
      val localFile = File(file.path)
      if (!localFile.exists()) {
        return@withContext Err(UploadError.ValidationError("File not found: ${file.path}"))
      }

      try {
        // Ensure we have a valid auth token if using OAuth
        if (config.auth is ApiAuth.OAuth && (oauthToken == null || System.currentTimeMillis() >= tokenExpirationTime)) {
          if (!authenticate()) {
            return@withContext Err(UploadError.AuthenticationError("Failed to obtain OAuth token"))
          }
        }

        val startTime = System.currentTimeMillis()

        // Create the upload request
        val response = httpClient.submitFormWithBinaryData(
          url = "${config.baseUrl}${config.uploadPath}",
          formData = formData {
            // Add the file
            append(config.fileFieldName, localFile.readBytes(), Headers.build {
              append(HttpHeaders.ContentDisposition, "filename=${localFile.name}")
              append(HttpHeaders.ContentType, guessMimeType(localFile.name))
            })

            // Add additional form fields if any
            config.additionalFields.forEach { (key, value) ->
              append(key, value)
            }
          }
        ) {
          method = HttpMethod.parse(config.uploadMethod)
          timeout {
            requestTimeoutMillis = config.timeoutMs
          }
        }

        val endTime = System.currentTimeMillis()
        val responseBody = response.bodyAsText()

        if (!response.status.isSuccess()) {
          return@withContext Err(
            UploadError.TransferError(
              message = "Upload failed with status ${response.status.value}",
              statusCode = response.status.value,
              response = responseBody
            )
          )
        }

        // Parse JSON response
        val json = try {
          Json.parseToJsonElement(responseBody).jsonObject
        } catch (e: Exception) {
          // Not JSON or invalid JSON
          null
        }

        // Extract URL and ID from JSON if patterns provided
        val url = if (json != null && config.urlJsonPath != null) {
          extractJsonValue(json, config.urlJsonPath)?.let { URL(it) }
        } else null

        val remoteId = if (json != null && config.fileIdJsonPath != null) {
          extractJsonValue(json, config.fileIdJsonPath)
        } else null

        val result = UploadPluginResult(
          success = true,
          url = url,
          remoteId = remoteId,
          size = localFile.length(),
          uploadTimeMs = endTime - startTime,
          metadata = mapOf(
            "statusCode" to response.status.value,
            "response" to responseBody
          )
        )

        Ok(result)
      } catch (e: ResponseException) {
        Err(mapResponseException(e))
      } catch (e: Exception) {
        Err(UploadError.UnknownError("Error during API upload: ${e.message}", e))
      }
    }

  /**
   * Extract a value from a nested JSON object using a dot-notation path.
   */
  private fun extractJsonValue(json: JsonObject, path: String): String? {
    val keys = path.split('.')
    var current: JsonElement = json

    for (key in keys) {
      if (current !is JsonObject) return null
      current = current[key] ?: return null
    }

    return when (current) {
      is JsonPrimitive -> current.contentOrNull
      else -> null
    }
  }

  /**
   * Map Ktor ResponseException to appropriate UploadError.
   */
  private fun mapResponseException(e: ResponseException): UploadError {
    return when (e) {
      is HttpRequestTimeoutException ->
        UploadError.TimeoutError("Request timed out", e)

      else -> {
        val statusCode = e.response.status.value
        when (statusCode) {
          in 400..499 -> when (statusCode) {
            401, 403 -> UploadError.AuthenticationError("Authentication failed: ${e.message}", e)
            413 -> UploadError.QuotaExceededError("File too large", e)
            else -> UploadError.TransferError("Client error: ${e.message}", statusCode, null, e)
          }

          in 500..599 -> UploadError.ServerError("Server error: ${e.message}", statusCode, e)
          else -> UploadError.TransferError("HTTP error: ${e.message}", statusCode, null, e)
        }
      }
    }
  }

  /**
   * Guess MIME type from filename extension.
   */
  private fun guessMimeType(filename: String): String {
    return when (filename.substringAfterLast('.', "").lowercase()) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "gif" -> "image/gif"
      "webp" -> "image/webp"
      "mp4" -> "video/mp4"
      "webm" -> "video/webm"
      "mp3" -> "audio/mpeg"
      "wav" -> "audio/wav"
      "pdf" -> "application/pdf"
      "zip" -> "application/zip"
      "json" -> "application/json"
      "xml" -> "application/xml"
      "txt" -> "text/plain"
      "html" -> "text/html"
      "css" -> "text/css"
      "js" -> "application/javascript"
      else -> "application/octet-stream"
    }
  }

  override suspend fun onExecutionSuccess(outputs: List<IOutputFile>, timing: ExecutionTiming) {
    // Divide by 2 because we're returning both input and output files when not deleting
    val uploadCount = if (config.deleteAfterUpload) outputs.size else outputs.size / 2
    println("[$uploadServiceType] Successfully uploaded $uploadCount files in ${timing.duration}")
  }

  override suspend fun onItemSuccess(
    input: IOutputFile,
    outputs: List<IOutputFile>,
    timing: ItemExecutionTiming,
  ) {
    val remoteItem = outputs.find { it.path != input.path }
    val uploadSpeed = if (timing.duration.inWholeMilliseconds > 0) {
      val bytesPerMs = input.size.toDouble() / timing.duration.inWholeMilliseconds
      val mbps = bytesPerMs * 8 / 1000 // Convert to Mbps
      String.format("%.2f Mbps", mbps)
    } else "∞ Mbps"

    println(
      "[$uploadServiceType] Uploaded ${input.path} (${formatFileSize(input.size)}) to " +
              "${remoteItem?.path ?: "remote location"} in ${timing.duration} ($uploadSpeed)"
    )
  }

  /**
   * Format file size in human readable format.
   */
  private fun formatFileSize(size: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var unitIndex = 0

    while (value >= 1024 && unitIndex < units.size - 1) {
      value /= 1024
      unitIndex++
    }

    return String.format("%.2f %s", value, units[unitIndex])
  }

  override suspend fun onItemError(
    input: IOutputFile,
    error: UploadError,
    timing: ItemExecutionTiming,
  ) {
    val errorDetails = when (error) {
      is UploadError.TransferError -> "${error.message} (Status: ${error.statusCode})"
      is UploadError.ServerError -> "${error.message} (Status: ${error.statusCode})"
      else -> error.message
    }

    println("[$uploadServiceType] Failed to upload ${input.path} after ${timing.duration}: $errorDetails")
  }
}
