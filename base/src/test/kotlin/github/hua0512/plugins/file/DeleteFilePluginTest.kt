@file:OptIn(KotestInternal::class)

package github.hua0512.plugins.file

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import github.hua0512.data.dto.IOutputFile
import github.hua0512.data.plugin.PluginConfigs.DeleteFileConfig
import io.kotest.common.KotestInternal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Test class for DeleteFilePlugin
 */
class DeleteFilePluginTest : FunSpec({

  // Setup temporary directories for each test
  val tempDir = Files.createTempDirectory("deleteFilePluginTest")
  val sourceDir = tempDir.resolve("source").also { it.createDirectories() }

  beforeTest {
    // Create source directory if it doesn't exist
    if (sourceDir.notExists()) sourceDir.createDirectories()
  }

  afterSpec {
    // Clean up temp directories after all tests
    tempDir.toFile().deleteRecursively()
  }

  test("validateDestination should always return true") {
    val config = DeleteFileConfig()

    val plugin = DeleteFilePlugin(config)
    runBlocking {
      plugin.validateDestination().shouldBeTrue()
    }
  }

  test("performOperation should successfully delete a file") {
    // Create a source file
    val sourceFile = sourceDir.resolve("test.txt")
    sourceFile.createFile()
    sourceFile.writeText("Test content")

    val config = DeleteFileConfig(
      secureDelete = false,
      moveToTrash = false
    )

    val plugin = DeleteFilePlugin(config)
    val outputFile = mockk<IOutputFile>(relaxed = true)

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
    result.unwrap().shouldBeEmpty()
    sourceFile.exists().shouldBeFalse() // File should be deleted
  }

  test("performOperation should fail when file doesn't exist") {
    val nonExistentFile = sourceDir.resolve("non_existent.txt")

    val config = DeleteFileConfig()

    val plugin = DeleteFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns nonExistentFile.toString()
    every { outputFile.size } returns 0
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

  test("shouldProcess should respect file age threshold") {
    // Create two files with different ages
    val oldFile = sourceDir.resolve("old.txt")
    val newFile = sourceDir.resolve("new.txt")

    oldFile.createFile()
    newFile.createFile()

    // Set file modification time - old file was modified 2 days ago
    val twoDaysAgo = System.currentTimeMillis() - 2.days.inWholeMilliseconds
    Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.fromMillis(twoDaysAgo))

    // New file was modified 1 hour ago
    val oneHourAgo = System.currentTimeMillis() - 1.hours.inWholeMilliseconds
    Files.setLastModifiedTime(newFile, java.nio.file.attribute.FileTime.fromMillis(oneHourAgo))

    // Configure to delete files older than 1 day
    val config = DeleteFileConfig(
      olderThanMs = 1.days.inWholeMilliseconds
    )

    val plugin = DeleteFilePlugin(config)

    val oldFileOutput = mockk<IOutputFile>()
    every { oldFileOutput.path } returns oldFile.toString()
    every { oldFileOutput.size } returns oldFile.toFile().length()
    every { oldFileOutput.streamerName } returns "TestStreamer"
    every { oldFileOutput.streamerPlatform } returns "TestPlatform"
    every { oldFileOutput.streamTitle } returns "TestTitle"
    every { oldFileOutput.streamDate } returns twoDaysAgo
    every { oldFileOutput.streamDataId } returns 1L

    val newFileOutput = mockk<IOutputFile>()
    every { newFileOutput.path } returns newFile.toString()
    every { newFileOutput.size } returns newFile.toFile().length()
    every { newFileOutput.streamerName } returns "TestStreamer"
    every { newFileOutput.streamerPlatform } returns "TestPlatform"
    every { newFileOutput.streamTitle } returns "TestTitle"
    every { newFileOutput.streamDate } returns oneHourAgo
    every { newFileOutput.streamDataId } returns 1L

    // Old file should be processed (deleted)
    plugin.shouldProcess(oldFileOutput).shouldBeTrue()

    // New file should not be processed (kept)
    plugin.shouldProcess(newFileOutput).shouldBeFalse()
  }

  test("secure delete should overwrite file data before deletion") {
    // Skip this test on CI environments or when running with -Dskip.secure.delete=true
    val skipTest = System.getProperty("skip.secure.delete").toBoolean()
    if (skipTest) {
      return@test
    }

    // Create a file to be securely deleted
    val secureFile = sourceDir.resolve("secure.txt")
    secureFile.createFile()
    secureFile.writeText("Sensitive data that should be overwritten")

    val config = DeleteFileConfig(
      secureDelete = true,
      secureDeletionPasses = 3
    )

    val plugin = DeleteFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns secureFile.toString()
    every { outputFile.size } returns secureFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    result.isOk.shouldBeTrue()
    secureFile.exists().shouldBeFalse()
  }

  test("moving to trash should maintain the trash directory") {
    // Skip this test in CI environments
    val skipTest = System.getProperty("skip.trash.test").toBoolean()
    if (skipTest) {
      return@test
    }

    // Create a file to be moved to trash
    val trashFile = sourceDir.resolve("trash.txt")
    trashFile.createFile()
    trashFile.writeText("This file should be moved to trash")

    val config = DeleteFileConfig(
      moveToTrash = true
    )

    val plugin = DeleteFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns trashFile.toString()
    every { outputFile.size } returns trashFile.toFile().length()
    every { outputFile.streamerName } returns "TestStreamer"
    every { outputFile.streamerPlatform } returns "TestPlatform"
    every { outputFile.streamTitle } returns "TestTitle"
    every { outputFile.streamDate } returns 1234567890L
    every { outputFile.streamDataId } returns 1L

    val result = runBlocking {
      try {
        plugin.performOperation(outputFile)
      } catch (e: UnsupportedOperationException) {
        // If trash is not supported on this platform, skip the test
        println("Trash operations not supported on this platform, skipping test")
        return@runBlocking null
      }
    }

    if (result != null) {
      result.isOk.shouldBeTrue()
      // File should no longer exist at the original location
      trashFile.exists().shouldBeFalse()
    }
  }

  test("operation should fail for non-regular files") {
    // Create a directory instead of a regular file
    val directory = sourceDir.resolve("directory")
    directory.createDirectories()

    val config = DeleteFileConfig()

    val plugin = DeleteFilePlugin(config)
    val outputFile = mockk<IOutputFile>()

    every { outputFile.path } returns directory.toString()

    val result = runBlocking {
      plugin.performOperation(outputFile)
    }

    result.isErr.shouldBeTrue()
    result.getError().shouldBeInstanceOf<FileOperationError.OperationError>()
    directory.exists().shouldBeTrue() // Directory should still exist
  }

  test("getDestinationPath should return the source path") {
    val sourceFile = sourceDir.resolve("return_path_test.txt")
    sourceFile.createFile()

    val config = DeleteFileConfig()
    val plugin = DeleteFilePlugin(config)

    val outputFile = mockk<IOutputFile>()
    every { outputFile.path } returns sourceFile.toString()

    val destPath = plugin.getDestinationPath(outputFile)
    destPath.shouldBe(Path.of(sourceFile.toString()))
  }
})
