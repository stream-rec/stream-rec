package github.hua0512.plugins.ffmpeg

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginConfigs.RemuxConfig
import github.hua0512.data.dto.IOutputFile
import github.hua0512.plugins.command.CommandError
import github.hua0512.plugins.command.CommandResult
import github.hua0512.utils.deleteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import kotlin.io.path.deleteExisting


private val ERROR_PATTERN = Regex(
  "\\b(error|missing|invalid|corrupt|illegal|overflow|out of range)\\b",
  RegexOption.IGNORE_CASE
)


/**
 * FFmpeg plugin that remuxes media files to different container formats
 * without re-encoding the streams.
 */
class RemuxPlugin(
  config: RemuxConfig,
) : BaseFFmpegPlugin<RemuxConfig>(config) {

  override val id: String = "c7a9168a-932b-4562-b21b-cfed87d55495"
  override val name: String = "FFmpeg Remux"
  override val description: String = "Remuxes media files to different container formats"
  override val version: String = "1.0.0"
  override val author: String = "hua0512"


  private companion object {
    private val logger = org.slf4j.LoggerFactory.getLogger(RemuxPlugin::class.java)
  }

  public override fun buildFFmpegOptions(input: IOutputFile, outputPath: Path): List<String> {
    val options = mutableListOf<String>()

    // Copy all streams without re-encoding if configured
    if (ffmpegConfig.copyAllStreams) {
      options.add("-c")
      options.add("copy")
    }

    // Map specific streams if configured
    ffmpegConfig.mapStreams?.let { mapStreams ->
      val streamMappings = mapStreams.split(",")
      for (mapping in streamMappings) {
        options.add("-map")
        options.add(mapping)
      }
    }

    // Set output format
    options.add("-f")
    options.add(ffmpegConfig.outputFormat)

    // Add format options
    for (formatOption in ffmpegConfig.formatOptions) {
      val parts = formatOption.split("=", limit = 2)
      if (parts.size == 2) {
        options.add("-${parts[0]}")
        options.add(parts[1])
      } else {
        options.add("-${formatOption}")
      }
    }

    // Preserve timestamps if configured
    if (ffmpegConfig.preserveTimestamps) {
      options.add("-copyts")
      options.add("-start_at_zero")
    }

    // Fix broken streams if configured
    if (ffmpegConfig.fixBrokenStreams) {
      options.add("-fflags")
      options.add("+discardcorrupt+genpts")
    }

    // Set max muxing queue size if configured
    ffmpegConfig.maxMuxingQueueSize?.let { size ->
      options.add("-max_muxing_queue_size")
      options.add(size.toString())
    }

    return options
  }

  override suspend fun validate(inputs: List<IOutputFile>): Result<Unit, CommandError> {
    // First perform the base validation
    val baseResult = super.validate(inputs)
    if (baseResult.isErr) {
      return baseResult.asErr()
    }

    // Additional checks specific to remuxing
    if (inputs.isEmpty()) {
      return Err(CommandError.ValidationError("No input files provided"))
    }

    // Check if FFmpeg is available
    return withContext(Dispatchers.IO) {
      try {
        val ffmpegPath = ffmpegConfig.ffmpegPath ?: "ffmpeg"
        val process = ProcessBuilder(ffmpegPath, "-version")
          .redirectOutput(ProcessBuilder.Redirect.PIPE)
          .redirectError(ProcessBuilder.Redirect.PIPE)
          .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
          return@withContext Err(CommandError.ValidationError("FFmpeg is not available or not working properly"))
        }

        // Check if output format is supported
        val formatCheckProcess = ProcessBuilder(ffmpegPath, "-formats")
          .redirectOutput(ProcessBuilder.Redirect.PIPE)
          .redirectError(ProcessBuilder.Redirect.PIPE)
          .start()

        val formats = formatCheckProcess.inputStream.bufferedReader().use { it.readText() }
        val formatRegex = "\\s${ffmpegConfig.outputFormat}\\s".toRegex()

        if (!formatRegex.containsMatchIn(formats)) {
          return@withContext Err(
            CommandError.ValidationError(
              "Output format '${ffmpegConfig.outputFormat}' may not be supported by your FFmpeg build"
            )
          )
        }

        Ok(Unit)
      } catch (e: Exception) {
        Err(CommandError.ValidationError("Failed to validate FFmpeg: ${e.message}", e))
      }
    }
  }

  public override fun getOutputPath(input: IOutputFile, extension: String?): Pair<Path, IOutputFile> {
    // Use the configured output format as extension if not specified
    val ext = extension ?: ffmpegConfig.outputExtension ?: ffmpegConfig.outputFormat
    return super.getOutputPath(input, ext)
  }

  override fun processCommandResult(result: CommandResult, inputs: List<IOutputFile>): List<IOutputFile> {
    val processedList = super.processCommandResult(result, inputs)

    logger.debug("process command result: {}", result)
    return if (processedList.size == 2) {
      val stdout = result.stdout
      logger.debug("FFmpeg output: $stdout")
      val stderr = result.stderr
      logger.debug("FFmpeg error output: $stderr")
      val errorSequence = ERROR_PATTERN.findAll(stderr)
      val hasError = errorSequence.any()
      logger.debug("FFmpeg has error: $hasError")


      logger.debug("Config :{}", config)

      if (config.deleteOriginalFilesAfterRemux) {
        if (hasError && !config.forceDeleteIfErrorInRemux) {
          logger.error("Remuxing failed with error: $stderr")
          return processedList
        }
        // Delete the original file after remuxing
        Path.of(processedList[0].path).deleteFile()
        logger.debug("Deleted original file: ${processedList[0].path}")
        // preserve the remuxed file
        return listOf(processedList[1])
      }
      // do not delete the original file if remuxing contains error
      return processedList
    } else processedList
  }

  override suspend fun onItemSuccess(
    input: IOutputFile,
    outputs: List<IOutputFile>,
    timing: ItemExecutionTiming,
  ) {
    // Find the output file (skip the input file in the outputs list)

    // check if its skipped
    val skippedResult = shouldProcessItem(input)
    if (!skippedResult) {
      return
    }

    val remuxedFile = outputs.find { it.path != input.path }

    if (remuxedFile != null) {
      val outputFile = File(remuxedFile.path)
      val compressionRatio = if (input.size > 0) remuxedFile.size.toDouble() / input.size.toDouble() else 1.0
      val formattedRatio = String.format("%.2f", compressionRatio)

      logger.debug(
        "Remuxed ${input.path} to ${ffmpegConfig.outputFormat} format " +
                "(${formatFileSize(input.size)} → ${formatFileSize(remuxedFile.size)}, " +
                "ratio: $formattedRatio) in ${timing.duration}"
      )
    } else {
      logger.error("Processed ${input.path} but no output file was found")
    }
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
}
