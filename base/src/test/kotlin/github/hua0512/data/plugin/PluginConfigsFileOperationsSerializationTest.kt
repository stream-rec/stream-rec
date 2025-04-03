package github.hua0512.data.plugin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class PluginConfigsFileOperationsSerializationTest : FunSpec({

  val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
      polymorphic(PluginConfigs::class) {
        subclass(PluginConfigs.DeleteFileConfig::class)
        subclass(PluginConfigs.MoveFileConfig::class)
        subclass(PluginConfigs.CopyFileConfig::class)
      }
    }
  }

  context("DeleteFileConfig serialization/deserialization") {
    test("should serialize and deserialize with all properties") {
      val config = PluginConfigs.DeleteFileConfig(
        secureDelete = true,
        secureDeletionPasses = 5,
        moveToTrash = false,
        olderThanMs = 86400000, // 1 day
        operationTimeoutMs = 5000,
        retryCount = 2,
        retryDelayMs = 1500,
        fileFilter = "*.tmp"
      )

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.DeleteFileConfig>(serialized)

      deserialized shouldBe config
    }

    test("should serialize and deserialize with default properties") {
      val config = PluginConfigs.DeleteFileConfig()

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.DeleteFileConfig>(serialized)

      deserialized shouldBe config
    }

    test("should deserialize correctly from polymorphic JSON") {
      val jsonString = """
                {
                    "type": "delete",
                    "secureDelete": true,
                    "secureDeletionPasses": 7,
                    "moveToTrash": true,
                    "fileFilter": "*.log"
                }
            """.trimIndent()

      val deserialized = json.decodeFromString<PluginConfigs>(jsonString)

      deserialized shouldBe PluginConfigs.DeleteFileConfig(
        secureDelete = true,
        secureDeletionPasses = 7,
        moveToTrash = true,
        fileFilter = "*.log"
      )
    }
  }

  context("MoveFileConfig serialization/deserialization") {
    test("should serialize and deserialize with all properties") {
      val config = PluginConfigs.MoveFileConfig(
        destinationDirectory = "/path/to/destination",
        overwriteExisting = true,
        operationTimeoutMs = 10000,
        retryCount = 3,
        retryDelayMs = 2000,
        createDirectories = true,
        preserveAttributes = false,
        fileFilter = "*.mp4|*.mkv"
      )

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.MoveFileConfig>(serialized)

      deserialized shouldBe config
    }

    test("should deserialize correctly from polymorphic JSON") {
      val jsonString = """
                {
                    "type": "move",
                    "destinationDirectory": "/target/dir",
                    "overwriteExisting": true,
                    "fileFilter": "*.avi"
                }
            """.trimIndent()

      val deserialized = json.decodeFromString<PluginConfigs>(jsonString)

      deserialized shouldBe PluginConfigs.MoveFileConfig(
        destinationDirectory = "/target/dir",
        overwriteExisting = true,
        fileFilter = "*.avi"
      )
    }
  }

  context("CopyFileConfig serialization/deserialization") {
    test("should serialize and deserialize with all properties") {
      val config = PluginConfigs.CopyFileConfig(
        destinationDirectory = "/path/to/copies",
        overwriteExisting = false,
        operationTimeoutMs = 15000,
        retryCount = 5,
        retryDelayMs = 1000,
        createDirectories = false,
        preserveAttributes = true,
        fileFilter = "*.txt|*.doc"
      )

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.CopyFileConfig>(serialized)

      deserialized shouldBe config
    }

    test("should deserialize correctly from polymorphic JSON") {
      val jsonString = """
                {
                    "type": "copy",
                    "destinationDirectory": "/backup/files",
                    "operationTimeoutMs": 30000,
                    "fileFilter": "*.mp3"
                }
            """.trimIndent()

      val deserialized = json.decodeFromString<PluginConfigs>(jsonString)

      deserialized shouldBe PluginConfigs.CopyFileConfig(
        destinationDirectory = "/backup/files",
        operationTimeoutMs = 30000,
        fileFilter = "*.mp3"
      )
    }
  }

  context("Polymorphic serialization/deserialization") {
    test("should preserve type information when serializing as base type") {
      val configs: List<PluginConfigs> = listOf(
        PluginConfigs.DeleteFileConfig(secureDelete = true, fileFilter = "*.temp"),
        PluginConfigs.MoveFileConfig(destinationDirectory = "/dest", overwriteExisting = true),
        PluginConfigs.CopyFileConfig(destinationDirectory = "/backup")
      )

      val serialized = json.encodeToString(configs)
      val deserialized = json.decodeFromString<List<PluginConfigs>>(serialized)

      deserialized shouldBe configs
    }

    test("should handle enabled property correctly") {
      val config = PluginConfigs.DeleteFileConfig()
      config.enabled = false

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.DeleteFileConfig>(serialized)

      deserialized.enabled shouldBe false
    }
  }
})
