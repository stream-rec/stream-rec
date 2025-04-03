package github.hua0512.plugins.api

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.dto.IOutputFile
import github.hua0512.data.plugin.ApiAuth
import github.hua0512.data.plugin.ExecutionTiming
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginConfigs.ApiEndpoint
import github.hua0512.data.plugin.PluginConfigs.ApiPluginConfig
import github.hua0512.plugins.action.AbstractProcessingPlugin
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * An API plugin that can make HTTP requests to external APIs as part of the processing pipeline.
 */
class ApiPlugin<I : IOutputFile>(
  val config: ApiPluginConfig,
) : AbstractProcessingPlugin<I, I, ApiError>() {

  companion object {
    private val logger = LoggerFactory.getLogger(ApiPlugin::class.java)
  }

  override val id: String = "8b68fcd2-9ad9-4c9d-af88-c9a283ee0f5e"
  override val name: String = "API Plugin"
  override val description: String = "API Plugin for making HTTP requests"
  override val version: String = "1.0"
  override val author: String = "hua0512"

  // Token storage for OAuth authentication
  private var oauthToken: String? = null
  private var tokenExpirationTime: Long = 0

  override val processInParallel: Boolean = false

  private val httpClient: HttpClient = HttpClient {
    install(HttpTimeout) {
      requestTimeoutMillis = config.timeoutMs
    }
    install(ContentNegotiation) {
      json()
    }
    install(HttpRequestRetry) {
      retryOnServerErrors(maxRetries = 3)
      exponentialDelay()
    }

    if (config.auth is ApiAuth.Basic) {
      install(Auth) {
        basic {
          credentials {
            BasicAuthCredentials(config.auth.username, config.auth.password)
          }
        }
      }
    }
  }


  override suspend fun validate(inputs: List<I>): Result<Unit, ApiError> {
    return Ok(Unit)
  }

  override suspend fun preExecution(inputs: List<I>) {
    // Ensure we are authenticated if needed
    if (config.auth is ApiAuth.OAuth) {
      authenticate()
    }
  }

  private suspend fun authenticate(): Boolean {
    val auth = config.auth ?: return true

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
          logger.error("OAuth authentication failed: ${response.status.value}")
          false
        }
      } catch (e: Exception) {
        logger.error("OAuth authentication error", e)
        false
      }
    }

    return true
  }

  override suspend fun processItem(input: I): Result<List<I>, ApiError> = withContext(Dispatchers.IO) {
    val file = File(input.path)
    if (!file.exists()) {
      return@withContext Err(ApiError("File not found: ${input.path}"))
    }

    try {
      // Process each API endpoint
      for (endpoint in config.endpoints) {
        val apiResponse = makeApiRequest(endpoint, file, input)

        // Handle the API response if needed
        if (apiResponse.statusCode !in 200..299) {
          return@withContext Err(
            ApiError(
              "API request to ${endpoint.name} failed with status ${apiResponse.statusCode}",
              apiResponse.statusCode,
              response = apiResponse.body
            )
          )
        }

        // You could potentially modify the input based on the API response
        // For example, add metadata from the API response to the input
        // This is just a pass-through for now
      }

      // Return the original input as we're not modifying it in this example
      Ok(listOf(input))
    } catch (e: ResponseException) {
      Err(mapResponseException(e))
    } catch (e: Exception) {
      Err(ApiError("Error making API request: ${e.message}", cause = e))
    }
  }

  private suspend fun makeApiRequest(
    endpoint: ApiEndpoint,
    file: File,
    inputFile: I,
  ): ApiResponse {
    val url = "${config.baseUrl}${endpoint.path}"

    // Prepare headers with authentication if needed
    val headersBuilder = HeadersBuilder()

    config.auth?.let { auth ->
      when (auth) {
        is ApiAuth.Bearer -> headersBuilder.append("Authorization", "Bearer $oauthToken")
        is ApiAuth.ApiKey -> headersBuilder.append(auth.headerName, auth.key)
        is ApiAuth.Basic -> {
          // Basic auth is handled by Ktor client configuration
        }

        is ApiAuth.OAuth -> {
          oauthToken?.let { token ->
            headersBuilder.append("Authorization", "Bearer $token")
          }
        }
      }
    }

    // Add custom headers
    endpoint.additionalHeaders.forEach { (key, value) ->
      headersBuilder.append(key, value.replacePlaceholders(inputFile))
    }

    val response = when (endpoint.method.uppercase()) {
      "POST", "PUT" -> {
        if (endpoint.contentType?.contains("multipart", ignoreCase = true) == true) {
          // Multipart form submission with file
          httpClient.submitFormWithBinaryData(
            url = url,
            formData = formData {
              // Add the file
              append(endpoint.formFieldName, file.readBytes(), Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=${file.name}")
                append(HttpHeaders.ContentType, guessMimeType(file.name))
              })

              // Add additional form fields
              endpoint.additionalFields.forEach { (key, value) ->
                append(key, value.replacePlaceholders(inputFile))
              }
            }
          ) {
            headers.appendAll(headersBuilder.build())
            timeout { requestTimeoutMillis = config.timeoutMs }
          }
        } else {
          // Regular POST/PUT request
          httpClient.request(url) {
            method = HttpMethod.parse(endpoint.method)
            headers.appendAll(headersBuilder.build())

            endpoint.contentType?.let {
              contentType(ContentType.parse(it))
            }

            // Add parameters as JSON body or form parameters
            if (endpoint.additionalFields.isNotEmpty()) {
              val fields = endpoint.additionalFields.mapValues { it.value.replacePlaceholders(inputFile) }

              if (endpoint.contentType?.contains("json", ignoreCase = true) == true) {
                setBody(JsonObject(fields.mapValues { JsonPrimitive(it.value) }))
              } else {
                setBody(FormDataContent(Parameters.build {
                  fields.forEach { (key, value) ->
                    append(key, value)
                  }
                }))
              }
            }

            timeout { requestTimeoutMillis = config.timeoutMs }
          }
        }
      }

      "GET", "DELETE" -> {
        // GET or DELETE request
        httpClient.request(url) {
          method = HttpMethod.parse(endpoint.method)
          headers.appendAll(headersBuilder.build())

          // Add query parameters
          if (endpoint.additionalFields.isNotEmpty()) {
            url {
              endpoint.additionalFields.forEach { (key, value) ->
                parameters.append(key, value.replacePlaceholders(inputFile))
              }
            }
          }

          timeout { requestTimeoutMillis = config.timeoutMs }
        }
      }

      else -> throw IllegalArgumentException("Unsupported HTTP method: ${endpoint.method}")
    }

    val responseBody = response.bodyAsText()

    // Parse response if it's JSON
    val jsonResponse = try {
      Json.parseToJsonElement(responseBody).jsonObject
    } catch (e: Exception) {
      null
    }

    // Extract values from response based on mapping
    val apiResponse = ApiResponse(
      statusCode = response.status.value,
      body = responseBody
    )
    return apiResponse
  }


  /**
   * Map Ktor ResponseException to appropriate ApiError.
   */
  private fun mapResponseException(e: ResponseException): ApiError {
    val statusCode = e.response.status.value
    return when (statusCode) {
      in 400..499 -> ApiError(
        "Client error: ${e.message}",
        statusCode = statusCode,
        cause = e
      )

      in 500..599 -> ApiError(
        "Server error: ${e.message}",
        statusCode = statusCode,
        cause = e
      )

      else -> ApiError(
        "HTTP error: ${e.message}",
        statusCode = statusCode,
        cause = e
      )
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

  override fun createExecutionError(message: String, cause: Throwable?): ApiError {
    return ApiError(message, cause = cause)
  }

  override suspend fun onExecutionSuccess(outputs: List<I>, timing: ExecutionTiming) {
    logger.info("[${config.name}] Successfully processed ${outputs.size} files in ${timing.duration}")
  }

  override suspend fun onItemSuccess(input: I, outputs: List<I>, timing: ItemExecutionTiming) {
    logger.info("[${config.name}] Successfully processed ${input.path} in ${timing.duration}")
  }

  override suspend fun onItemError(input: I, error: ApiError, timing: ItemExecutionTiming) {
    val errorDetails = if (error.statusCode != null) {
      "${error.message} (Status: ${error.statusCode})"
    } else {
      error.message
    }

    logger.error("[${config.name}] Failed to process ${input.path} after ${timing.duration}: $errorDetails")
  }


}
