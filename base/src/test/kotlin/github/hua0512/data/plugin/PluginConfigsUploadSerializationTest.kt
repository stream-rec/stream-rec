package github.hua0512.data.plugin

import github.hua0512.data.upload.UploadPlatform
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class PluginConfigsUploadSerializationTest : FunSpec({

  val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
      polymorphic(PluginConfigs::class) {
        subclass(PluginConfigs.UploadConfig.RcloneConfig::class)
        subclass(PluginConfigs.UploadConfig.ApiUploadConfig::class)
        subclass(PluginConfigs.UploadConfig.CommandUploadConfig::class)
      }
    }
  }

  context("RcloneConfig serialization/deserialization") {
    test("should serialize and deserialize with all properties") {
      val config = PluginConfigs.UploadConfig.RcloneConfig(
        remoteName = "gdrive:",
        remotePath = "/backups/{date}",
        rclonePath = "/usr/bin/rclone",
        configFile = "/home/user/.config/rclone/rclone.conf",
        additionalFlags = listOf("--progress", "--fast-list"),
        createPublicLinks = true,
        transferMode = "copy",
        bufferSize = "16M",
        transfers = 4,
        bandwidthLimit = "10M",
        checksum = true,
        timeoutMs = 3600000,
        retryCount = 3,
        retryDelayMs = 5000,
        fileFilter = "*.mp4|*.mkv",
        deleteAfterUpload = false,
        validateAfterUpload = true
      )

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.UploadConfig.RcloneConfig>(serialized)

      deserialized shouldBe config
    }

    test("should serialize and deserialize with default properties") {
      val config = PluginConfigs.UploadConfig.RcloneConfig()

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.UploadConfig.RcloneConfig>(serialized)

      deserialized shouldBe config
    }

    test("should deserialize correctly from polymorphic JSON") {
      val jsonString = """
        {
          "type": "rclone",
          "remoteName": "dropbox:",
          "remotePath": "/videos",
          "createPublicLinks": true,
          "transferMode": "move",
          "deleteAfterUpload": true
        }
      """.trimIndent()

      val deserialized = json.decodeFromString<PluginConfigs>(jsonString)

      deserialized shouldBe PluginConfigs.UploadConfig.RcloneConfig(
        remoteName = "dropbox:",
        remotePath = "/videos",
        createPublicLinks = true,
        transferMode = "move",
        deleteAfterUpload = true
      )
    }
  }

  context("ApiUploadConfig serialization/deserialization") {
    test("should serialize and deserialize with all properties") {
      val config = PluginConfigs.UploadConfig.ApiUploadConfig(
        baseUrl = "https://api.example.com",
        uploadPath = "/upload",
        authPath = "/auth",
        uploadMethod = "POST",
        headers = mapOf("X-API-Key" to "abc123"),
        auth = ApiAuth.Bearer(token = "token123"),
        fileFieldName = "uploadFile",
        additionalFields = mapOf("description" to "Test upload"),
        urlJsonPath = "response.url",
        fileIdJsonPath = "response.id",
        timeoutMs = 60000,
        retryCount = 2,
        retryDelayMs = 3000,
        fileFilter = "*.jpg|*.png",
        deleteAfterUpload = true,
        validateAfterUpload = true
      )

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.UploadConfig.ApiUploadConfig>(serialized)

      deserialized shouldBe config
    }

    test("should deserialize correctly from polymorphic JSON") {
      val jsonString = """
        {
          "baseUrl": "https://api.upload.com",
          "uploadPath": "/v1/files",
          "headers": {
            "Authorization": "Bearer token123"
          },
          "urlJsonPath": "data.fileUrl"
        }
      """.trimIndent()

      val deserialized = json.decodeFromString<PluginConfigs.UploadConfig.ApiUploadConfig>(jsonString)

      deserialized shouldBe PluginConfigs.UploadConfig.ApiUploadConfig(
        baseUrl = "https://api.upload.com",
        uploadPath = "/v1/files",
        headers = mapOf("Authorization" to "Bearer token123"),
        urlJsonPath = "data.fileUrl"
      )
    }
  }

  context("CommandUploadConfig serialization/deserialization") {
    test("should serialize and deserialize with all properties") {
      val config = PluginConfigs.UploadConfig.CommandUploadConfig(
        uploadCommandTemplate = "upload-tool --file {file}",
        testConnectionCommand = "upload-tool --test",
        authCommand = "upload-tool --login",
        urlExtractionPattern = "URL:\\s*(https://\\S+)",
        remoteIdExtractionPattern = "ID:\\s*(\\w+)",
        useShell = true,
        uploadCommandArgs = listOf("--progress", "--public"),
        timeoutMs = 120000,
        retryCount = 4,
        retryDelayMs = 2000,
        fileFilter = "*.zip",
        deleteAfterUpload = false,
        validateAfterUpload = true
      )

      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.UploadConfig.CommandUploadConfig>(serialized)

      deserialized shouldBe config
    }

    test("should deserialize correctly from polymorphic JSON") {
      val jsonString = """
        {
          "uploadCommandTemplate": "custom-uploader {file}",
          "useShell": true,
          "urlExtractionPattern": "Download URL: (.*)"
        }
      """.trimIndent()

      val deserialized = json.decodeFromString<PluginConfigs.UploadConfig.CommandUploadConfig>(jsonString)

      deserialized shouldBe PluginConfigs.UploadConfig.CommandUploadConfig(
        uploadCommandTemplate = "custom-uploader {file}",
        useShell = true,
        urlExtractionPattern = "Download URL: (.*)"
      )
    }
  }

  context("UploadConfig polymorphic serialization/deserialization") {
    test("should preserve type information when serializing as base type") {
      val configs: List<PluginConfigs.UploadConfig> = listOf(
        PluginConfigs.UploadConfig.RcloneConfig(remoteName = "s3:", transferMode = "sync"),
        PluginConfigs.UploadConfig.ApiUploadConfig(baseUrl = "https://api.test.com", uploadPath = "/upload"),
        PluginConfigs.UploadConfig.CommandUploadConfig(uploadCommandTemplate = "ftp-upload {file}")
      )

      val serialized = json.encodeToString(configs)
      val deserialized = json.decodeFromString<List<PluginConfigs.UploadConfig>>(serialized)

      deserialized shouldBe configs
    }

    test("should handle platform property correctly") {
      val rcloneConfig = PluginConfigs.UploadConfig.RcloneConfig()
      rcloneConfig.platform shouldBe UploadPlatform.RCLONE

      val apiConfig = PluginConfigs.UploadConfig.ApiUploadConfig(
        baseUrl = "https://example.com",
        uploadPath = "/upload"
      )
      apiConfig.platform shouldBe UploadPlatform.EXTERNAL

      val commandConfig = PluginConfigs.UploadConfig.CommandUploadConfig(
        uploadCommandTemplate = "upload {file}"
      )
      commandConfig.platform shouldBe UploadPlatform.EXTERNAL
    }

    test("should handle enabled property correctly") {
      val config = PluginConfigs.UploadConfig.RcloneConfig()
      config.enabled shouldBe true

      config.enabled = false
      val serialized = json.encodeToString(config)
      val deserialized = json.decodeFromString<PluginConfigs.UploadConfig.RcloneConfig>(serialized)

      deserialized.enabled shouldBe false
    }
  }
})
