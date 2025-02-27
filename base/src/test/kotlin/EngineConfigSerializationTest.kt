import github.hua0512.data.config.engine.EngineConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class EngineConfigSerializationTest : FunSpec({
  val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }

  test("should serialize and deserialize StreamlinkConfig") {
    val config = EngineConfig.StreamlinkConfig(
      useBuiltInSegmenter = true,
      exitDownloadOnError = true
    )
    val serialized = json.encodeToString<EngineConfig>(config)
    val deserialized = json.decodeFromString<EngineConfig>(serialized)

    deserialized shouldBe config
    serialized shouldBe """
            {
                "type": "streamlink",
                "useBuiltInSegmenter": true,
                "exitDownloadOnError": true
            }
        """.trimIndent()
  }

  test("should use default values for StreamlinkConfig") {
    val config = EngineConfig.StreamlinkConfig()
    val serialized = json.encodeToString<EngineConfig>(config)
    val deserialized = json.decodeFromString<EngineConfig>(serialized)

    deserialized shouldBe config
    serialized shouldBe """
            {
                "type": "streamlink",
                "useBuiltInSegmenter": false,
                "exitDownloadOnError": false
            }
        """.trimIndent()
  }

  test("should serialize and deserialize FFmpegConfig") {
    val config = EngineConfig.FFmpegConfig(
      useBuiltInSegmenter = true,
      exitDownloadOnError = true
    )
    val serialized = json.encodeToString<EngineConfig>(config)
    val deserialized = json.decodeFromString<EngineConfig>(serialized)

    deserialized shouldBe config
    serialized shouldBe """
            {
                "type": "ffmpeg",
                "useBuiltInSegmenter": true,
                "exitDownloadOnError": true
            }
        """.trimIndent()
  }

  test("should use default values for FFmpegConfig") {
    val config = EngineConfig.FFmpegConfig()
    val serialized = json.encodeToString<EngineConfig>(config)
    val deserialized = json.decodeFromString<EngineConfig>(serialized)

    deserialized shouldBe config
    serialized shouldBe """
            {
                "type": "ffmpeg",
                "useBuiltInSegmenter": false,
                "exitDownloadOnError": false
            }
        """.trimIndent()
  }

  test("should serialize and deserialize KotlinConfig") {
    val config = EngineConfig.KotlinConfig(
      enableFlvFix = true,
      enableFlvDuplicateTagFiltering = true,
      combineTsFiles = true
    )
    val serialized = json.encodeToString<EngineConfig>(config)
    val deserialized = json.decodeFromString<EngineConfig>(serialized)

    deserialized shouldBe config
    serialized shouldBe """
            {
                "type": "kotlin",
                "enableFlvFix": true,
                "enableFlvDuplicateTagFiltering": true,
                "combineTsFiles": true
            }
        """.trimIndent()
  }

  test("should use default values for KotlinConfig") {
    val config = EngineConfig.KotlinConfig()
    val serialized = json.encodeToString<EngineConfig>(config)
    val deserialized = json.decodeFromString<EngineConfig>(serialized)

    deserialized shouldBe config
    serialized shouldBe """
            {
                "type": "kotlin"
            }
        """.trimIndent()
  }
})