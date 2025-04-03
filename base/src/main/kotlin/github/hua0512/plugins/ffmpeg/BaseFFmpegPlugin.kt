package github.hua0512.plugins.ffmpeg

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.dto.IOutputFile
import github.hua0512.plugins.command.BaseCommandPlugin
import github.hua0512.plugins.command.CommandError
import github.hua0512.plugins.command.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Base class for FFmpeg-based plugins.
 * Provides common functionality for working with FFmpeg.
 */
abstract class BaseFFmpegPlugin<T : FFmpegConfig>(
  protected val ffmpegConfig: T,
) : BaseCommandPlugin<T>(ffmpegConfig) {

  override val commandType: String = "ffmpeg"

  companion object {
    internal const val SIZE_PATTERN = "size=\\s*(\\d+)([a-zA-Z]?B)"
  }

  override fun shouldOutputLine(line: String): Boolean {
    val sizeRegex = Regex(SIZE_PATTERN)
    val sizeMatch = sizeRegex.find(line)
    // do not output ffmpeg progress information
    return !(line.startsWith("frame=") || line.startsWith("Press [q]") || (line.startsWith("[hls") && line.contains("Opening") && line.contains(
      "for reading"
    )) || sizeMatch != null)
  }

  /**
   * Builds FFmpeg command-specific options for this operation.
   * To be implemented by specific FFmpeg plugin implementations.
   *
   * @param input The input file to process
   * @param outputPath The output path where the result will be saved
   * @return List of FFmpeg options
   */
  protected abstract fun buildFFmpegOptions(input: IOutputFile, outputPath: Path): List<String>


  /**
   * Extracts media information from the input file.
   * This information can be used to make decisions about processing.
   *
   * @param input The input file to analyze
   * @return Media information or null if analysis fails
   */
  protected suspend fun getMediaInfo(input: IOutputFile): MediaInfo? = withContext(Dispatchers.IO) {
    try {
      val ffprobePath = ffmpegConfig.ffmpegPath?.let {
        Paths.get(it).parent.resolve("ffprobe").toString()
      } ?: "ffprobe"

      val command = mutableListOf(
        ffprobePath,
        "-v", "quiet",
        "-print_format", "json",
        "-show_format",
        "-show_streams",
        input.path
      )

      val process = ProcessBuilder(command)
        .directory(File(ffmpegConfig.workingDirectory ?: "."))
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

      val output = process.inputStream.bufferedReader().use { it.readText() }
      val exitCode = process.waitFor()

      if (exitCode == 0) {
        // In a real implementation, we would parse this JSON into a proper MediaInfo object
        // For simplicity, we're just returning a basic object
        val file = File(input.path)
        return@withContext MediaInfo(
          path = input.path,
          fileName = file.name,
          durationSeconds = 0.0, // Would be extracted from JSON
          formatName = "", // Would be extracted from JSON
          metaJson = output // Store the full JSON for later use
        )
      }

      return@withContext null
    } catch (e: Exception) {
      println("Error getting media info: ${e.message}")
      return@withContext null
    }
  }

  /**
   * Determines the output file path for the processed file.
   */
  protected open fun getOutputPath(input: IOutputFile, extension: String? = null): Pair<Path, IOutputFile> {
    val inputFile = File(input.path)
    val outputExt = extension ?: ffmpegConfig.outputExtension ?: inputFile.extension
    val fileName = "${inputFile.nameWithoutExtension}.${outputExt}"

    return if (ffmpegConfig.outputDirectory != null) {
      val outputDir = Path.of(ffmpegConfig.outputDirectory!!)
      outputDir.resolve(fileName) to input
    } else {
      inputFile.toPath().parent.resolve(fileName) to input
    }
  }

  override fun buildCommand(inputs: List<IOutputFile>): List<String> {
    if (inputs.isEmpty()) {
      throw IllegalArgumentException("No input files provided")
    }

    // FFmpeg plugins process one file at a time
    val input = inputs.first()
    val ffmpegPath = ffmpegConfig.ffmpegPath ?: "ffmpeg"

    // Get output path for this input
    val (outputPath, _) = getOutputPath(input)

    // Create parent directories if needed
    outputPath.parent?.let { Files.createDirectories(it) }

    val command = mutableListOf<String>()

    // Start with FFmpeg executable
    command.add(ffmpegPath)

    // Add global options
    command.add("-hide_banner")

    // Set logging level
    command.add("-loglevel")
    command.add(ffmpegConfig.logLevel)

    // Add overwrite flag if configured
    if (ffmpegConfig.overwrite) {
      command.add("-y")
    } else {
      command.add("-n")
    }

    // Add other global options
    command.addAll(ffmpegConfig.globalOptions)

    // Add input file
    command.add("-i")
    command.add(input.path)

    // Add operation-specific options
    command.addAll(buildFFmpegOptions(input, outputPath))

    // Add metadata if specified
    ffmpegConfig.metadata.forEach { (key, value) ->
      command.add("-metadata")
      command.add("$key=$value")
    }

    // Add output path
    command.add(outputPath.toString())

    return command
  }

  override fun processCommandResult(result: CommandResult, inputs: List<IOutputFile>): List<IOutputFile> {
    if (inputs.isEmpty()) {
      return emptyList()
    }

    // ffmpeg processes one file at a time, so we only need the first input
    val input = inputs[0]
    val (outputPath, _) = getOutputPath(input)

    // Create an output file object for the processed file
    val outputFile = if (Files.exists(outputPath)) {
      object : IOutputFile {
        override var path: String = outputPath.toString()
        override var size: Long = Files.size(outputPath)
        override var streamerName: String? = input.streamerName
        override var streamerPlatform: String? = input.streamerPlatform
        override var streamTitle: String? = input.streamTitle
        override var streamDate: Long? = input.streamDate
        override var streamDataId: Long = input.streamDataId
      }
    } else {
      null
    }

    return if (outputFile != null) {
      // check if input exists
      val input = Files.exists(Paths.get(input.path)).run { input } // If input exists, return it
      listOfNotNull(input, outputFile) // Return both input and output
    } else {
      listOf(input) // Return just input if output wasn't created
    }
  }

  override fun validateCommand(command: List<String>): Result<Unit, CommandError> {
    // Always allow FFmpeg commands - they're built by our code, not user input
    return Ok(Unit)
  }

  /**
   * Basic representation of media file information.
   * In a real implementation, this would be more comprehensive.
   */
  data class MediaInfo(
    val path: String,
    val fileName: String,
    val durationSeconds: Double,
    val formatName: String,
    val metaJson: String,
  )
}
