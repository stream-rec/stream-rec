package github.hua0512.plugins.ffmpeg

import github.hua0512.plugins.command.CommandConfig

/**
 * Base configuration for FFmpeg-based plugins.
 */
interface FFmpegConfig : CommandConfig {
  /**
   * Path to the FFmpeg executable.
   * If null, assumes ffmpeg is in the system PATH.
   */
  val ffmpegPath: String?

  /**
   * Global FFmpeg options that apply to all operations.
   * These are added before the input file.
   */
  val globalOptions: List<String>

  /**
   * Whether to overwrite existing output files.
   */
  val overwrite: Boolean

  /**
   * Log level for FFmpeg operations.
   * Valid values: "quiet", "panic", "fatal", "error", "warning", "info", "verbose", "debug", "trace"
   */
  val logLevel: String

  /**
   * Additional metadata to add to output files.
   */
  val metadata: Map<String, String>

  /**
   * Output file extension to use (without the dot).
   * If null, the extension will be determined based on the output format.
   */
  val outputExtension: String?

  /**
   * Output directory for processed files.
   * If null, files will be output next to input files.
   */
  val outputDirectory: String?
}
