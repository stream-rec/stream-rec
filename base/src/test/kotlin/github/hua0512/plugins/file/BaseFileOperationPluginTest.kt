package github.hua0512.plugins.file

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import github.hua0512.data.dto.IOutputFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeText

class BaseFileOperationPluginTest : FunSpec({

  // Create temporary test directories
  val testRootDir = Files.createTempDirectory("file-plugin-test")
  val sourceDir = testRootDir.resolve("source").apply { createDirectories() }
  val destDir = testRootDir.resolve("dest").apply { createDirectories() }

  // Test implementation of OutputFile
  class TestOutputFile(
    override var path: String,
    override var size: Long,
    override var streamerName: String? = "",
    override var streamerPlatform: String? = "",
    override var streamTitle: String? = "",
    override var streamDate: Long? = System.currentTimeMillis(),
    override var streamDataId: Long = 1,
  ) : IOutputFile {
    fun copy(path: String): IOutputFile =
      TestOutputFile(path, size, streamerName, streamerPlatform, streamTitle, streamDate, streamDataId)
  }

  // Concrete implementation for testing BaseFileOperationPlugin
  class TestFileOperationPlugin(
    config: FileOperationConfig,
    private val operationImpl: suspend (IOutputFile) -> Result<List<IOutputFile>, FileOperationError> = { file ->
      val destPath = destDir.resolve(File(file.path).name)
      Files.copy(Path.of(file.path), destPath, StandardCopyOption.REPLACE_EXISTING)
      Ok(
        listOf(
          TestOutputFile(
            destPath.toString(),
            destPath.toFile().length(),
            file.streamerName,
            file.streamerPlatform,
            file.streamTitle,
            file.streamDate,
            file.streamDataId
          )
        )
      )
    },
  ) : BaseFileOperationPlugin<FileOperationConfig>(config) {
    override val operationType: String = "test-operation"

    override suspend fun performOperation(file: IOutputFile): Result<List<IOutputFile>, FileOperationError> {
      return operationImpl(file)
    }

    override fun getDestinationPath(sourceFile: IOutputFile): Path {
      return destDir.resolve(File(sourceFile.path).name)
    }

    suspend fun testProcessItem(input: IOutputFile): Result<List<IOutputFile>, FileOperationError> {
      return processItem(input)
    }

    override suspend fun validateDestination(): Boolean = true
    override val id: String = ""
    override val name: String = "Test File Operation"
    override val description: String = "Test file operation plugin"
    override val version: String = "1.0.0"
    override val author: String = "System"
  }

  afterSpec {
    // Clean up test directories
    testRootDir.toFile().deleteRecursively()
  }

  test("should successfully process a file") {
    // Arrange
    val testFile = sourceDir.resolve("test-file.txt").apply {
      createFile()
      writeText("Test content")
    }
    val inputFile = TestOutputFile(
      testFile.toString(), size = testFile.toFile().length(),
      streamerName = null,
      streamerPlatform = null,
      streamTitle = null,
      streamDate = null,
      streamDataId = 0
    )
    val config = object : FileOperationConfig {

      override val fileFilter: String? = null
      override val createDirectories: Boolean = true
      override val preserveAttributes: Boolean = true
      override val overwriteExisting: Boolean = true
      override val retryCount: Int = 2
      override val retryDelayMs: Long = 10
      override val operationTimeoutMs: Long = 5000
    }

    val plugin = TestFileOperationPlugin(config)

    val result = plugin.testProcessItem(inputFile)

    result.isOk shouldBe true
    result.unwrap().shouldBeInstanceOf<List<IOutputFile>>()
    val outputFiles = result.value
    outputFiles.size shouldBe 1
    val outputPath = Path.of(outputFiles[0].path)
    outputPath.exists() shouldBe true
    Files.readString(outputPath) shouldBe "Test content"
  }

  test("should retry failed operations") {
    // Arrange
    val testFile = sourceDir.resolve("retry-test.txt").apply {
      createFile()
      writeText("Retry content")
    }
    val inputFile = TestOutputFile(testFile.toString(), testFile.toFile().length())


    val config = object : FileOperationConfig {

      override val fileFilter: String? = null
      override val createDirectories: Boolean = true
      override val preserveAttributes: Boolean = false
      override val overwriteExisting: Boolean = true
      override val retryCount: Int = 2
      override val retryDelayMs: Long = 10
      override val operationTimeoutMs: Long = 1000
    }

    var attemptCount = 0
    val plugin = TestFileOperationPlugin(config) { file ->
      attemptCount++
      if (attemptCount < 2) {
        Err(FileOperationError.OperationError("Test failure"))
      } else {
        val destPath = destDir.resolve(File(file.path).name)
        Files.copy(Path.of(file.path), destPath)
        Ok(listOf(TestOutputFile(destPath.toString(), destPath.toFile().length())))
      }
    }

    val result = plugin.testProcessItem(inputFile)

    result.isOk shouldBe true
    result.unwrap().shouldBeInstanceOf<List<IOutputFile>>()
    attemptCount shouldBe 2
    destDir.resolve("retry-test.txt").exists() shouldBe true
  }

  test("should apply file filter") {
    // Arrange
    val matchingFile = sourceDir.resolve("matching.txt").apply {
      createFile()
      writeText("Should process")
    }
    val nonMatchingFile = sourceDir.resolve("nonmatching.bin").apply {
      createFile()
      writeText("Should not process")
    }


    val config = object : FileOperationConfig {

      // Only match .txt files
      override val fileFilter: String? = ".*\\.txt$"
      override val createDirectories: Boolean = true
      override val preserveAttributes: Boolean = false
      override val overwriteExisting: Boolean = true
      override val retryCount: Int = 0
      override val retryDelayMs: Long = 0
      override val operationTimeoutMs: Long = 1000
    }

    var processCalled = false
    val plugin = TestFileOperationPlugin(config) { file ->
      processCalled = true
      val destPath = destDir.resolve(File(file.path).name)
      Files.copy(Path.of(file.path), destPath)
      Ok(listOf(TestOutputFile(destPath.toString(), destPath.toFile().length())))
    }

    // Act & Assert for matching file
    val matchingInput = TestOutputFile(matchingFile.toString(), matchingFile.toFile().length())
    processCalled = false
    val matchingResult = plugin.testProcessItem(matchingInput)

    matchingResult.isOk shouldBe true
    matchingResult.unwrap().shouldBeInstanceOf<List<IOutputFile>>()
    processCalled shouldBe true
    destDir.resolve("matching.txt").exists() shouldBe true

    // Act & Assert for non-matching file
    val nonMatchingInput = TestOutputFile(nonMatchingFile.toString(), nonMatchingFile.toFile().length())
    processCalled = false
    val nonMatchingResult = plugin.testProcessItem(nonMatchingInput)

    nonMatchingResult.isOk shouldBe true
    nonMatchingResult.unwrap().shouldBeInstanceOf<List<IOutputFile>>()
    processCalled shouldBe false  // Should skip processing
    destDir.resolve("nonmatching.bin").exists() shouldBe false
  }
})
