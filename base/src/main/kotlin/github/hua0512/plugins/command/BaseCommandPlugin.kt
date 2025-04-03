package github.hua0512.plugins.command

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.ExecutionTiming
import github.hua0512.data.dto.IOutputFile
import github.hua0512.plugins.action.AbstractProcessingPlugin
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base implementation for command execution plugins with common functionality
 * such as process handling, security checks, and error handling.
 *
 * @param config Configuration for command execution.
 */
abstract class BaseCommandPlugin<T : CommandConfig>(
  protected val config: T,
) : AbstractProcessingPlugin<IOutputFile, IOutputFile, CommandError>(), CommandPlugin {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(BaseCommandPlugin::class.java)
  }

  // Keep track of running processes for cleanup if needed
  private val runningProcesses = ConcurrentHashMap<Uuid, Process>()

  // Counter for command executions
  private val executionCounter = AtomicInteger(0)

  // Compiled pattern if fileFilter is specified
  private val fileFilterPattern: Pattern? by lazy {
    config.fileFilter?.let { Pattern.compile(it) }
  }

  override val maxConcurrentCommands: Int = 5

  override val batchMode: Boolean = false

  /**
   * Build the command to execute.
   *
   * @param inputs The inputs to process.
   * @return The command as a list of strings.
   */
  protected abstract fun buildCommand(inputs: List<IOutputFile>): List<String>

  /**
   * Check if a line of output should be processed.
   * This is used to filter output lines based on specific criteria.
   * @param line The output line to check.
   * @return True if the line should be processed, false otherwise.
   */
  open fun shouldOutputLine(line: String): Boolean = true

  /**
   * Process the command result to generate output files.
   *
   * @param result The command execution result.
   * @param inputs The original input files.
   * @return A list of output files.
   */
  protected abstract fun processCommandResult(
    result: CommandResult,
    inputs: List<IOutputFile>,
  ): List<IOutputFile>

  /**
   * Validate the command before execution.
   * This method should check if the command is secure and allowed.
   *
   * @param command The command to validate.
   * @return Result containing Unit if valid, or a SecurityError if not.
   */
  protected open fun validateCommand(command: List<String>): Result<Unit, CommandError> {
    // Security check: ensure the command is in the allowed list if specified
    if (config.allowedCommands.isNotEmpty()) {
      val executable =
        command.firstOrNull()?.lowercase() ?: return Err(CommandError.SecurityError("Empty command provided"))

      if (!config.allowedCommands.any { allowed ->
          executable.endsWith(allowed.lowercase()) ||
                  Paths.get(executable).fileName.toString().lowercase() == allowed.lowercase()
        }) {
        return Err(
          CommandError.SecurityError(
            "Command not in allowed list: $executable"
          )
        )
      }
    }

    return Ok(Unit)
  }

  override suspend fun validate(inputs: List<IOutputFile>): Result<Unit, CommandError> {
    // Check if any files exist
    if (inputs.isEmpty()) {
      return Err(CommandError.ValidationError("No input files provided"))
    }

    // Check if all input files exist
    for (input in inputs) {
      val file = File(input.path)
      if (!file.exists()) {
        return Err(CommandError.ValidationError("Input file does not exist: ${input.path}"))
      }

      // Check if file matches filter pattern
      if (fileFilterPattern != null && !shouldProcessItem(input)) {
        return Ok(Unit)
//        return Err(
//          CommandError.ValidationError(
//            "Input file does not match filter pattern: ${input.path}"
//          )
//        )
      }
    }

    // If workingDirectory is specified, check if it exists
    config.workingDirectory?.let {
      val workDir = File(it)
      if (!workDir.exists() || !workDir.isDirectory) {
        return Err(
          CommandError.ValidationError(
            "Working directory does not exist: $it"
          )
        )
      }
      if (!workDir.canRead() || !workDir.canWrite()) {
        return Err(
          CommandError.ValidationError(
            "Insufficient permissions for working directory: $it"
          )
        )
      }
    }

    return Ok(Unit)
  }

  override suspend fun processItem(input: IOutputFile): Result<List<IOutputFile>, CommandError> {
    return if (batchMode) {
      // For batch mode, we'll handle this in process() method
      Ok(listOf(input))
    } else {
      // Non-batch mode: execute command for each input
      if (shouldProcessItem(input)) {
        executeCommand(listOf(input))
      } else {
        // If the item doesn't match the filter, return it as is
        Ok(listOf(input))
      }
    }
  }

  override suspend fun process(inputs: List<IOutputFile>): Result<List<IOutputFile>, CommandError> {
    // Filter inputs based on filter pattern
    val filteredInputs = inputs.filter { shouldProcessItem(it) }

    if (filteredInputs.isEmpty()) {
      return Ok(inputs)  // No files to process, return original inputs
    }

    if (batchMode) {
      // In batch mode, execute a single command for all inputs
      return executeCommand(filteredInputs)
    }

    // For non-batch mode, the individual execution is handled by processItem()
    return super.process(inputs)
  }

  @OptIn(ExperimentalUuidApi::class)
  private suspend fun executeCommand(inputs: List<IOutputFile>): Result<List<IOutputFile>, CommandError> {
    val command = buildCommand(inputs)

    // Validate the command first
    val validationResult = validateCommand(command)
    if (validationResult.isErr) {
      return validationResult.asErr()
    }

    val executionId = Uuid.random()
    val executionNumber = executionCounter.incrementAndGet()

    var process: Process? = null

    try {
      val file = inputs.first()
      val outputDir = createTempOutputDir(executionNumber, file)
      val outputFile = outputDir.resolve("stdout_$executionNumber.txt").toFile()
      val errorFile = outputDir.resolve("stderr_${executionNumber}.txt").toFile()

      val startTime = System.currentTimeMillis()

      val processBuilder = ProcessBuilder(command)
        .directory(config.workingDirectory?.let { File(it) } ?: Path.of(inputs.first().path).parent?.toFile())

      // Add custom environment variables
      val env = processBuilder.environment()
      for ((key, value) in config.environmentVariables) {
        env[key] = value
      }

      // Configure output redirection
      if (config.redirectOutput) {
        processBuilder.redirectOutput(outputFile)
      } else {
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
      }

      if (config.redirectError) {
        processBuilder.redirectError(errorFile)
      } else {
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE)
      }

      // Start the process
      process = processBuilder.start()
//      // write input files to process
//      process.outputStream.use {
//        inputs.joinToString("\n") { it.path }.byteInputStream().copyTo(it)
//      }
      runningProcesses[executionId] = process

      var stdout = ""
      var stderr = ""
      var exitCode: Int = 0

      // Wait for the process to complete with timeout
      try {
        fun execute() {
          exitCode = process.waitFor()

          // Read output if needed
          stdout = readOutput(config.redirectOutput, process.inputStream, outputFile)
          stderr = readOutput(config.redirectError, process.errorStream, errorFile)
        }
        // Execute with timeout if specified
        if (config.timeoutMs > 0) {
          withTimeout(config.timeoutMs) {
            execute()
          }
        } else {
          execute()
        }
      } catch (e: TimeoutCancellationException) {
        // Process exceeded timeout
        process.destroyForcibly()
        return Err(
          CommandError.TimeoutError(
            message = "Command execution timed out after ${config.timeoutMs}ms",
            command = command
          )
        )
      }

      val endTime = System.currentTimeMillis()
      val executionTime = endTime - startTime

      // Create command result
      val result = CommandResult(
        id = executionNumber,
        command = command,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        executionTimeMs = executionTime,
        generatedFiles = findGeneratedFiles(outputDir)
      )

      // Check if command succeeded
      if (config.failOnNonZeroExit && exitCode != 0) {
        return Err(
          CommandError.ExecutionError(
            message = "Command execution failed with exit code $exitCode",
            exitCode = exitCode,
            stderr = stderr
          )
        )
      }

      // Process results to get output files
      val outputFiles = processCommandResult(result, inputs)
      logger.debug("Command result: {}", result)

      // delete stdout and stderr files
      outputFile.deleteFile()
      errorFile.deleteFile()

      return Ok(outputFiles)

    } catch (e: IOException) {
      return Err(
        CommandError.ExecutionError(
          message = "I/O error during command execution: ${e.message}",
          cause = e
        )
      )
    } catch (e: SecurityException) {
      return Err(
        CommandError.SecurityError(
          message = "Security error during command execution: ${e.message}",
          cause = e
        )
      )
    } catch (e: Exception) {
      return Err(
        CommandError.ExecutionError(
          message = "Unexpected error during command execution: ${e.message}",
          cause = e
        )
      )
    } finally {
      // Clean up resources
      process?.destroyForcibly()
      runningProcesses.remove(executionId)
    }
  }

  /**
   * Helper method to read output from either a process stream or a file.
   *
   * @param isRedirected Whether the output is redirected to a file
   * @param processStream The process stream to read from if not redirected
   * @param file The file to read from if redirected
   * @return The content read from the stream or file, limited to maxOutputSize
   */
  private fun readOutput(isRedirected: Boolean, processStream: InputStream, file: File): String {
    return if (!isRedirected) {
      processStream.bufferedReader().use { reader ->
        val line = reader.readText()
        if (shouldOutputLine(line)) line else ""
      }
    } else {
      if (file.exists()) {
        file.inputStream().bufferedReader().use { reader ->
          val line = reader.readText()
          if (shouldOutputLine(line)) line else ""
        }
      } else ""
    }.take(config.maxOutputSize)
  }

  /**
   * Create a temporary directory for command outputs.
   */
  private suspend fun createTempOutputDir(executionNumber: Int, inputFile: IOutputFile): Path =
    withContext(Dispatchers.IO) {
      val tempDir = config.workingDirectory?.let {
        Paths.get(it).resolve("command_output_$executionNumber")
      } ?: Path(inputFile.path).parent ?: Files.createTempDirectory("command_output_$executionNumber")

      // Set permissions to prevent other users from accessing our files
      try {
        val permissions = setOf(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE
        )
        Files.setPosixFilePermissions(tempDir, permissions)
      } catch (e: UnsupportedOperationException) {
        // Filesystem doesn't support POSIX permissions, fall back to Java's security
        tempDir.toFile().setReadable(true, true)
        tempDir.toFile().setWritable(true, true)
        tempDir.toFile().setExecutable(true, true)
      }

      tempDir
    }

  /**
   * Find files generated by the command execution.
   */
  private suspend fun findGeneratedFiles(outputDir: Path): List<Path> = withContext(Dispatchers.IO) {
    val files = mutableListOf<Path>()

    if (outputDir.exists()) {
      Files.walk(outputDir).use { paths ->
        paths.filter { Files.isRegularFile(it) }
          .filter { it.fileName.toString() != "stdout.txt" && it.fileName.toString() != "stderr.txt" }
          .forEach { files.add(it) }
      }
    }

    files
  }

  /**
   * Check if file should be processed based on filter pattern.
   */
  open suspend fun shouldProcessItem(file: IOutputFile): Boolean {
    fileFilterPattern?.let { pattern ->
      val fileName = File(file.path).name
      return pattern.matcher(fileName).matches().also {
        if (!it) {
          logger.debug("File $fileName does not match filter pattern: ${pattern.pattern()}")
        }
      }
    }
    return true
  }

  override fun createExecutionError(message: String, cause: Throwable?): CommandError {
    return CommandError.ExecutionError(message, null, null, cause)
  }

  override suspend fun onExecutionError(error: CommandError, timing: ExecutionTiming) {
    val message = when (error) {
      is CommandError.ExecutionError ->
        "Command execution failed: ${error.message}${error.exitCode?.let { ", exit code: $it" } ?: ""}"

      is CommandError.TimeoutError ->
        "Command timed out: ${error.message} for command: ${error.command.joinToString(" ")}"

      is CommandError.ValidationError ->
        "Command validation failed: ${error.message}"

      is CommandError.ConfigurationError ->
        "Command configuration error: ${error.message}"

      is CommandError.SecurityError ->
        "Command security error: ${error.message}"
    }

    println("[$commandType] Error after ${timing.duration}: $message")
  }

  override suspend fun onExecutionSuccess(outputs: List<IOutputFile>, timing: ExecutionTiming) {
    println("[$commandType] Successfully processed ${outputs.size} files in ${timing.duration}")
  }
}
