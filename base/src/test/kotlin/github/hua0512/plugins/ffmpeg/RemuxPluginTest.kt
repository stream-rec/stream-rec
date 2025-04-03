package github.hua0512.plugins.ffmpeg


import github.hua0512.data.dto.IOutputFile
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginConfigs.RemuxConfig
import github.hua0512.plugins.command.CommandError
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.spyk
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

class RemuxPluginTest : FunSpec({

  lateinit var remuxPlugin: RemuxPlugin

  @MockK
  lateinit var mockOutputFile: IOutputFile

  beforeTest {
    MockKAnnotations.init(this)

    mockOutputFile = mockk<IOutputFile>(relaxed = true)
    // Set up default behavior for mock output file
    every { mockOutputFile.path } returns "d:/test/input.flv"
    every { mockOutputFile.size } returns 10485760 // 10MB

    // Create default config for the plugin
    val config = RemuxConfig(
      outputDirectory = "d:/test/output",
      ffmpegPath = "ffmpeg",
      outputFormat = "mp4",
      copyAllStreams = true,
      preserveTimestamps = true,
      fixBrokenStreams = false,
      formatOptions = listOf("movflags=faststart"),
      outputExtension = null,
      maxMuxingQueueSize = 1024
    )

    remuxPlugin = spyk(RemuxPlugin(config))
  }

  test("Plugin should have correct metadata") {
    remuxPlugin.id shouldBe "c7a9168a-932b-4562-b21b-cfed87d55495"
    remuxPlugin.name shouldBe "FFmpeg Remux"
    remuxPlugin.description shouldBe "Remuxes media files to different container formats"
    remuxPlugin.version shouldBe "1.0.0"
    remuxPlugin.author shouldBe "System"
  }

  test("buildFFmpegOptions should correctly build options based on config") {
    val outputPath = Paths.get("d:/test/output/result.mp4")
    val options = remuxPlugin.buildFFmpegOptions(mockOutputFile, outputPath)

    assertSoftly {
      options shouldContainAll listOf("-c", "copy")
      options shouldContainAll listOf("-f", "mp4")
      options shouldContainAll listOf("-movflags", "faststart")
      options shouldContainAll listOf("-copyts", "-start_at_zero")
      options shouldContainAll listOf("-max_muxing_queue_size", "1024")
    }
  }

  test("buildFFmpegOptions should include map streams when configured") {
    // Update config to include map streams
    val configWithMapping = RemuxConfig(
      outputDirectory = "d:/test/output",
      ffmpegPath = "ffmpeg",
      outputFormat = "mp4",
      copyAllStreams = true,
      mapStreams = "0:v,0:a",
      preserveTimestamps = true,
      fixBrokenStreams = false,
      formatOptions = listOf()
    )

    remuxPlugin = RemuxPlugin(configWithMapping)
    val outputPath = Paths.get("d:/test/output/result.mp4")
    val options = remuxPlugin.buildFFmpegOptions(mockOutputFile, outputPath)

    assertSoftly {
      options shouldContain "-map"
      options shouldContain "0:v"
      options shouldContain "0:a"
    }
    println("Options: $options")
  }

  test("buildFFmpegOptions should include fix broken streams when configured") {
    // Update config to include fix broken streams
    val configWithFixStreams = RemuxConfig(
      outputDirectory = "d:/test/output",
      ffmpegPath = "ffmpeg",
      outputFormat = "mp4",
      copyAllStreams = true,
      preserveTimestamps = false,
      fixBrokenStreams = true,
      formatOptions = listOf()
    )

    remuxPlugin = RemuxPlugin(configWithFixStreams)
    val outputPath = Paths.get("d:/test/output/result.mp4")
    val options = remuxPlugin.buildFFmpegOptions(mockOutputFile, outputPath)

    assertSoftly {
      options shouldContain "-fflags"
      options shouldContain "+discardcorrupt+genpts"
    }
  }

  test("validate should return Ok when FFmpeg is available and format is supported") {
    val result = remuxPlugin.validate(listOf(mockOutputFile))
    println(result)
    // Verify result is ok
    result.isOk.shouldBeTrue()
  }

  test("validate should return error when output format is not supported") {
    // Mock format check with unsupported format
    val invalidOutput = "INVALID" // INVALID
    val remuxPlugin = spyk<RemuxPlugin>(
      RemuxPlugin(
        RemuxConfig(
          outputDirectory = "d:/test/output",
          ffmpegPath = "ffmpeg",
          outputFormat = invalidOutput,
          copyAllStreams = true,
          preserveTimestamps = true,
          fixBrokenStreams = false,
          formatOptions = listOf("movflags=faststart"),
          outputExtension = null,
          maxMuxingQueueSize = 1024
        )
      )
    )

    val result = remuxPlugin.validate(listOf(mockOutputFile))

    result.isErr.shouldBeTrue()
    result.error.shouldBeInstanceOf<CommandError.ValidationError>()
  }


  test("validate should return error when FFmpeg is not available") {
    val remuxPlugin = spyk<RemuxPlugin>(
      RemuxPlugin(
        RemuxConfig(
          outputDirectory = "d:/test/output",
          ffmpegPath = "INVALID_FFMPEG_PATH",
          outputFormat = "mp4",
          copyAllStreams = true,
          preserveTimestamps = true,
          fixBrokenStreams = false,
          formatOptions = listOf("movflags=faststart"),
          outputExtension = null,
          maxMuxingQueueSize = 1024
        )
      )
    )

    val result = remuxPlugin.validate(listOf(mockOutputFile))

    result.isErr.shouldBeTrue()
    result.error.shouldBeInstanceOf<CommandError.ValidationError>()
  }


  test("getOutputPath should use configured output format as extension") {
    val (path, _) = remuxPlugin.getOutputPath(mockOutputFile, null)

    path.toString().shouldContain(".mp4")
  }

  test("getOutputPath should use specified extension when provided") {
    val (path, _) = remuxPlugin.getOutputPath(mockOutputFile, "mkv")

    path.toString().shouldContain(".mkv")
  }

  test("onItemSuccess should log details about remuxing") {
    val mockInputFile = mockOutputFile
    mockOutputFile = mockk<IOutputFile>()
    every { mockOutputFile.path } returns "d:/test/output/input.flv"
    every { mockOutputFile.size } returns 5242880 // 5MB

    val timing = ItemExecutionTiming(1000L, 2000L, 2.seconds, 0)

    // We're not actually testing console output, just verifying function completes successfully
    remuxPlugin.onItemSuccess(mockInputFile, listOf(mockInputFile, mockOutputFile), timing)
  }
})
