package github.hua0512.plugins.upload

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.PluginConfigs.UploadConfig
import github.hua0512.data.plugin.UploadError
import github.hua0512.data.plugin.UploadPluginResult
import github.hua0512.data.dto.IOutputFile
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult
import github.hua0512.data.upload.UploadState
import github.hua0512.plugins.action.AbstractProcessingPlugin
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.upload.UploadRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * Base implementation for upload plugins with common functionality.
 *
 * @param config Configuration for the upload operations.
 */
abstract class BaseUploadPlugin<T : UploadConfig>(
  protected val config: T,
  protected val uploadSemaphore: Semaphore,
  protected val streamRepo: StreamDataRepo,
  protected val uploadRepository: UploadRepo,
) : AbstractProcessingPlugin<IOutputFile, IOutputFile, UploadError>(), UploadPlugin {

  companion object {

    protected val logger: Logger = LoggerFactory.getLogger(BaseUploadPlugin::class.java)
  }

  // Track active uploads for monitoring and cleanup if needed
  private val activeUploads = ConcurrentHashMap<String, UUID>()

  // Counter for uploads
  private val uploadCounter = AtomicInteger(0)

  // Compiled pattern if fileFilter is specified
  private val fileFilterPattern: Pattern? by lazy {
    config.fileFilter?.let { Pattern.compile(it) }
  }

  /**
   * Performs the actual upload operation for a single file.
   *
   * @param file The file to upload.
   * @return Result containing upload result or error.
   */
  protected abstract suspend fun performUpload(file: IOutputFile): Result<UploadPluginResult, UploadError>

  /**
   * Validates the uploaded file if needed.
   *
   * @param localFile The local file that was uploaded.
   * @param uploadResult The result of the upload operation.
   * @return Result indicating success or validation error.
   */
  protected open suspend fun validateUpload(
    localFile: IOutputFile,
    uploadResult: UploadPluginResult,
  ): Result<Unit, UploadError> {
    if (!config.validateAfterUpload) {
      return Ok(Unit)
    }

    // Basic validation - check if size matches if available
    if (uploadResult.size != null && localFile.size != uploadResult.size) {
      return Err(
        UploadError.ValidationError(
          "Size mismatch: local file size ${localFile.size} bytes, " +
                  "uploaded file size ${uploadResult.size} bytes"
        )
      )
    }

    return Ok(Unit)
  }

  override suspend fun processItem(input: IOutputFile): Result<List<IOutputFile>, UploadError> {
    // Check if the file exists and should be processed
    val file = File(input.path)
    if (!file.exists()) {
      return Err(UploadError.ValidationError("File does not exist: ${input.path}"))
    }

    if (!shouldProcess(file)) {
      return Ok(listOf(input))  // Skip but return the original file
    }

    val uploadId = UUID.randomUUID()

    // Limit concurrent uploads
    return uploadSemaphore.withPermit {
      try {
        // Mark this file as being uploaded
        activeUploads[input.path] = uploadId
        val savedUploadData = uploadRepository.findUploadDataByPath(input.path) ?: UploadData(
          id = 0,
          filePath = input.path,
          status = UploadState.NOT_STARTED,
          config = config,
        ).also {
          // Bind the upload data to the stream data
          it.streamDataId = input.streamDataId
          uploadRepository.insertUploadData(it)
        }

        // Upload with retries
        val result = retryUpload(input, savedUploadData)

        // If successful and configured to delete, remove the local file
        if (result.isOk && config.deleteAfterUpload) {
          try {
            file.delete()
          } catch (e: Exception) {
            logger.error("Warning: Failed to delete local file after upload: ${e.message}")
          }
        }

        // Handle the result
        return when {
          result.isOk -> {
            val uploadResult = result.value

            // If we're not deleting the local file, return both local and remote
            return@withPermit if (config.deleteAfterUpload) {
              Ok(listOf())
            } else {
              Ok(listOf(input))
            }
          }

          result.isErr -> result.asErr()
          else -> throw IllegalStateException("Unexpected result type")
        }
      } finally {
        // Remove from active uploads
        activeUploads.remove(input.path)
      }
    }
  }

  private suspend fun retryUpload(input: IOutputFile, uploadData: UploadData): Result<UploadPluginResult, UploadError> {
    var lastError: UploadError? = null

    var uploadDataRecord = uploadData
    for (attempt in 0..config.retryCount) {
      if (attempt > 0) {
        delay(config.retryDelayMs)
        logger.info("Retrying upload of ${input.path} (attempt ${attempt + 1}/${config.retryCount + 1})")
        uploadDataRecord = uploadDataRecord.copy(
          status = UploadState.REUPLOADING,
        )
        uploadRepository.updateUploadData(uploadDataRecord)
      }

      var startTime = System.currentTimeMillis()
      val result = withTimeoutOrNull(config.timeoutMs) {
        uploadDataRecord = uploadDataRecord.copy(
          status = UploadState.UPLOADING,
        )
        uploadRepository.updateUploadData(uploadDataRecord)
        performUpload(input)
      }
      val endTime = System.currentTimeMillis()

      when {
        result == null -> {
          lastError = UploadError.TimeoutError("Upload timed out after ${config.timeoutMs}ms")
          uploadDataRecord = uploadDataRecord.copy(
            status = UploadState.FAILED,
          )
          uploadRepository.updateUploadData(uploadDataRecord)
          val uploadResult = UploadResult(
            id = uploadDataRecord.id,
            startTime = startTime,
            endTime = endTime,
            isSuccess = false,
            message = lastError.message,
            uploadDataId = uploadDataRecord.id,
          )
          uploadRepository.saveResult(uploadResult)
        }

        result.isOk -> {
          // Validate the upload if configured
          val validationResult = validateUpload(input, result.value)
          if (validationResult.isErr) {
            lastError = validationResult.error
            continue
          }
          val uploadResult = UploadResult(
            id = uploadDataRecord.id,
            startTime = startTime,
            endTime = endTime,
            isSuccess = true,
            message = "",
            uploadDataId = uploadDataRecord.id,
          )
          uploadRepository.saveResult(uploadResult)
          return result
        }

        result.isErr -> {
          lastError = result.error
          uploadDataRecord = uploadDataRecord.copy(
            status = UploadState.FAILED,
          )
          uploadRepository.updateUploadData(uploadDataRecord)

          val uploadResult = UploadResult(
            id = uploadDataRecord.id,
            startTime = startTime,
            endTime = endTime,
            isSuccess = false,
            message = lastError.message,
            uploadDataId = uploadDataRecord.id,
          )
          uploadRepository.saveResult(uploadResult)

          // Some errors don't make sense to retry
          if (lastError is UploadError.AuthenticationError ||
            lastError is UploadError.ConfigurationError ||
            lastError is UploadError.ValidationError
          ) {
            return result
          }
        }
      }
    }

    return Err(lastError ?: UploadError.UnknownError("Unknown error during upload"))
  }

  /**
   * Determines if a file should be processed based on filter patterns.
   */
  protected fun shouldProcess(file: File): Boolean {
    fileFilterPattern?.let { pattern ->
      return pattern.matcher(file.name).matches()
    }

    return true
  }

  override suspend fun validate(inputs: List<IOutputFile>): Result<Unit, UploadError> {
    // Check if any files exist
    if (inputs.isEmpty()) {
      return Err(UploadError.ValidationError("No input files provided"))
    }

    // Test connection to the upload service
    if (!testConnection()) {
      return Err(
        UploadError.ConnectionError(
          "Cannot connect to $uploadServiceType service"
        )
      )
    }

    // Authenticate with the service if needed
    if (!authenticate()) {
      return Err(
        UploadError.AuthenticationError(
          "Authentication failed for $uploadServiceType service"
        )
      )
    }

    // Validate each input file
    inputs.forEach { input ->
      if (input.streamDataId == 0L) {
        return Err(UploadError.ValidationError("Stream data ID is missing for file: ${input.path}"))
      }
    }

    return Ok(Unit)
  }

  override fun createExecutionError(message: String, cause: Throwable?): UploadError {
    return UploadError.UnknownError(message, cause)
  }
}
