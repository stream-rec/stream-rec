package github.hua0512.plugins.file

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import github.hua0512.data.dto.IOutputFile
import github.hua0512.data.plugin.PluginConfigs.CopyFileConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.io.path.*

/**
 * Test class for the CopyFilePlugin.
 */
class CopyFilePluginTest : FunSpec({

  // Setup temporary directories for each test
  val tempDir = Files.createTempDirectory("copyFilePluginTest")
  val sourceDir = tempDir.resolve("source").also { it.createDirectories() }
  val destDir = tempDir.resolve("destination")

  beforeTest {
    // Create source directory if it doesn't exist
    if (sourceDir.notExists()) sourceDir.createDirectories()

    // Clean and recreate destination directory
    if (destDir.exists()) {
      destDir.toFile().deleteRecursively()
    }
    destDir.createDirectories()
  }

  afterSpec {
    // Clean up temp directories after all tests
    tempDir.toFile().deleteRecursively()
  }

  test("validateDestination should return true for existing writable directory") {
    val config = CopyFileConfig(
      destinationDirectory = destDir.toString(),
      createDirectories = true,
      overwriteExisting = false,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    runBlocking {
      plugin.validateDestination().shouldBeTrue()
    }
  }

  test("validateDestination should create directory when it doesn't exist and createDirectories is true") {
    val newDestDir = tempDir.resolve("new_dest")

    val config = CopyFileConfig(
      destinationDirectory = newDestDir.toString(),
      createDirectories = true,
      overwriteExisting = false,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    runBlocking {
      plugin.validateDestination().shouldBeTrue()
    }

    newDestDir.exists().shouldBeTrue()
  }

  test("validateDestination should return false when directory doesn't exist and createDirectories is false") {
    val nonExistingDir = tempDir.resolve("non_existing")

    val config = CopyFileConfig(
      destinationDirectory = nonExistingDir.toString(),
      createDirectories = false,
      overwriteExisting = false,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    runBlocking {
      plugin.validateDestination().shouldBeFalse()
    }

    nonExistingDir.exists().shouldBeFalse()
  }

  test("performOperation should successfully copy a file") {
    // Create a source file
    val sourceFile = sourceDir.resolve("test.txt")
    sourceFile.createFile()
    sourceFile.writeText("Test content")

    val config = CopyFileConfig(
      destinationDirectory = destDir.toString(),
      createDirectories = true,
      overwriteExisting = true,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    val outputFile = mockk<IOutputFile>()


    every { outputFile.path } returns sourceFile.toString()
    every { outputFile.size } returns sourceFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    result.isOk.shouldBeTrue()
    result.unwrap().shouldHaveSize(1)
    val copiedFile = destDir.resolve("test.txt")
    copiedFile.exists().shouldBeTrue()
    copiedFile.deleteExisting()
  }

  test("performOperation should preserve file attributes when preserveAttributes is true") {
    // Create a source file with specific attributes
    val sourceFile = sourceDir.resolve("test_attrs.txt")
    sourceFile.createFile()
    sourceFile.writeText("Test content")

    val originalTime = FileTime.fromMillis(1000000)
    Files.setAttribute(sourceFile, "lastModifiedTime", originalTime)

    val config = CopyFileConfig(
      destinationDirectory = destDir.toString(),
      createDirectories = true,
      overwriteExisting = true,
      preserveAttributes = true
    )

    val plugin = CopyFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns sourceFile.toString()
    every { outputFile.size } returns sourceFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    result.isOk.shouldBeTrue()

    val destFile = destDir.resolve("test_attrs.txt")
    destFile.exists().shouldBeTrue()

    val destAttrs = Files.readAttributes(destFile, BasicFileAttributes::class.java)
    destAttrs.lastModifiedTime().shouldBe(originalTime)
  }

  test("performOperation should fail when source file doesn't exist") {
    val nonExistentFile = sourceDir.resolve("non_existent.txt")

    val config = CopyFileConfig(
      destinationDirectory = destDir.toString(),
      createDirectories = true,
      overwriteExisting = false,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns nonExistentFile.toString()
    every { outputFile.size } returns nonExistentFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    result.isErr.shouldBeTrue()
    result.getError().shouldBeInstanceOf<FileOperationError.SourceFileNotFoundError>()
  }

  test("performOperation should handle destination conflict with overwriting") {
    // Create source file
    val sourceFile = sourceDir.resolve("overwrite.txt")
    sourceFile.createFile()
    sourceFile.writeText("New content")

    // Create a conflicting destination file
    val destFile = destDir.resolve("overwrite.txt")
    destFile.createFile()
    destFile.writeText("Old content")

    val config = CopyFileConfig(
      destinationDirectory = destDir.toString(),
      createDirectories = true,
      overwriteExisting = true,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns sourceFile.toString()
    every { outputFile.size } returns sourceFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    result.isOk.shouldBeTrue()
    destFile.exists().shouldBeTrue()
    String(Files.readAllBytes(destFile)).shouldBe("New content")
  }

  test("performOperation should handle destination conflict with error") {
    // Create source file
    val sourceFile = sourceDir.resolve("skip.txt")
    sourceFile.createFile()
    sourceFile.writeText("New content")

    // Create a conflicting destination file
    val destFile = destDir.resolve("skip.txt")
    destFile.createFile()
    destFile.writeText("Old content")

    val config = CopyFileConfig(
      destinationDirectory = destDir.toString(),
      createDirectories = true,
      overwriteExisting = false,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns sourceFile.toString()
    every { outputFile.size } returns destFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    // Operation should return success
    result.isErr.shouldBeTrue()
    result.error.shouldBeInstanceOf<FileOperationError.InvalidDestinationError>()

    String(Files.readAllBytes(destFile)).shouldBe("Old content")
  }

  test("performOperation should handle destination path placeholders") {
    // Create a source file
    val sourceFile = sourceDir.resolve("placeholder.txt")
    sourceFile.createFile()
    sourceFile.writeText("Test content")

    val config = CopyFileConfig(
      destinationDirectory = "$destDir/{streamer}/{platform}/{title}",
      createDirectories = true,
      overwriteExisting = true,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns sourceFile.toString()
    every { outputFile.size } returns sourceFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    result.isOk.shouldBeTrue()
    destDir.resolve("TestStreamer/TestPlatform/TestTitle/placeholder.txt").exists().shouldBeTrue()

  }

  test("performOperation should return an error when destination directory cannot be created") {
    // This can be tricky to test, but we can try creating a read-only parent dir
    val readOnlyDir = tempDir.resolve("readonly")
    readOnlyDir.createDirectories()
    val markResult = readOnlyDir.toFile().setReadable(false)

    if (!markResult) {
      Files.setAttribute(readOnlyDir, "dos:readonly", true)
    }

    val impossibleDestDir = readOnlyDir.resolve("impossible")

    val config = CopyFileConfig(
      destinationDirectory = impossibleDestDir.toString(),
      createDirectories = true,
      overwriteExisting = true,
      preserveAttributes = false
    )

    val plugin = CopyFilePlugin(config)
    val sourceFile = sourceDir.resolve("test2.txt").apply {
      createFile()
      writeText("Test content")
    }

    val outputFile = mockk<IOutputFile>()
    every { outputFile.path } returns sourceFile.toString()
    every { outputFile.size } returns sourceFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    if (result.isOk) {
      println("Operation succeeded unexpectedly with result: ${result.value}")
    }

    result.isErr.shouldBeTrue()
    val error = result.error
    error.shouldBeInstanceOf<FileOperationError>()
    error.message.shouldContain("Failed to create directory")

    // Clean up read-only permission
    readOnlyDir.toFile().setWritable(true)
  }
})
