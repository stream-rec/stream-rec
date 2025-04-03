package github.hua0512.dao.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import github.hua0512.data.plugin.PluginConfigs
import github.hua0512.utils.mainLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class Migrate13To14(val json: Json) : Migration(13, 14) {


  override fun migrate(connection: SQLiteConnection) {

    val actionTableQueryStatement = connection.prepare(
      """
      SELECT UploadAction.id, UploadAction.uploadConfig, UploadData.id FROM UploadAction JOIN UploadData ON UploadData.uploadActionId = UploadAction.id
    """.trimIndent()
    )

    // Add new uploadConfig to the UploadData table
    connection.execSQL(
      """
      ALTER TABLE UploadData ADD COLUMN uploadConfig TEXT
    """.trimIndent()
    )

    actionTableQueryStatement.use {
      while (actionTableQueryStatement.step()) {
        val id = actionTableQueryStatement.getLong(0)
        var config = actionTableQueryStatement.getText(1)
        val uploadDataId = actionTableQueryStatement.getLong(2)

        val oldConfigJson = json.decodeFromString<JsonObject>(config)

        // check if type is rclone
        val isRclone = oldConfigJson["type"]?.jsonPrimitive?.content?.contains("RcloneConfig") == true
        if (isRclone) {
          config = json.encodeToString<PluginConfigs>(getNewRcloneConfig(oldConfigJson))
        } else {
          mainLogger.warn("migration(13,14) --- UploadAction $id is not RcloneConfig, skipping")
        }

        // Insert the uploadConfig into the UploadData table
        connection.execSQL(
          """
          UPDATE UploadData SET uploadConfig = '$config' WHERE id = $uploadDataId
        """.trimIndent()
        )
      }
    }

    // Drop uploadData uploadConfig column

    connection.execSQL(
      """
CREATE TABLE IF NOT EXISTS UploadData_backup (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filePath` TEXT NOT NULL, `status` INTEGER NOT NULL, `streamDataId` INTEGER NOT NULL DEFAULT 0, `uploadConfig` TEXT NOT NULL, FOREIGN KEY(`streamDataId`) REFERENCES `StreamData`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
    """.trimIndent()
    )
    connection.execSQL(
      """
      INSERT INTO UploadData_backup (id, filePath, status, streamDataId, uploadConfig) SELECT id, filePath, status, streamDataId, uploadConfig FROM UploadData
    """.trimIndent()
    )

    connection.execSQL(
      """
      DROP TABLE IF EXISTS UploadData
    """.trimIndent()
    )
    connection.execSQL(
      """
      ALTER TABLE UploadData_backup RENAME TO UploadData
    """.trimIndent()
    )

    connection.execSQL(
      """
        CREATE INDEX IF NOT EXISTS `index_UploadData_streamDataId` ON `UploadData` (`streamDataId`)
      """.trimIndent()
    )

    // Drop the old UploadAction table
    connection.execSQL(
      """
      DROP TABLE IF EXISTS UploadAction
    """.trimIndent()
    )

    /**
     * {"type":"template","onPartedDownload":[{"type":"rclone","rcloneOperation":"copy","remotePath":"onedrive:records/{streamer}/%m/%d/","args":["--config","/opt/records/rclone.conf","--use-mmap","--onedrive-chunk-size","200M"]},{"type":"remove"},{"type":"command","program":"sh","args":["-c","find /opt/records -type d -empty -delete"]}],"onStreamingFinished":[]}
     */
    val streamerConfigUpdateStatement = connection.prepare(
      """
      SELECT id, download_config FROM streamer
    """.trimIndent()
    )

    streamerConfigUpdateStatement.use {
      while (streamerConfigUpdateStatement.step()) {
        val id = streamerConfigUpdateStatement.getLong(0)
        val downloadConfig = streamerConfigUpdateStatement.getText(1)
        val oldConfigMap = json.decodeFromString<JsonObject>(downloadConfig).toMutableMap()
        val onPartedDownload = oldConfigMap["onPartedDownload"]?.jsonArray?.let { migrateToNewPluginActions(it) }
        val onStreamingFinished = oldConfigMap["onStreamingFinished"]?.jsonArray?.let { migrateToNewPluginActions(it) }
        // update the config map
        if (onPartedDownload != null) {
          oldConfigMap["onPartedDownload"] = onPartedDownload as JsonElement
        }
        if (onStreamingFinished != null) {
          oldConfigMap["onStreamingFinished"] = onStreamingFinished as JsonElement
        }
//        mainLogger.debug("migration(13,14) --- streamer $id download_config: $oldConfigMap")

        // update the downloadConfig
        val newConfig = json.encodeToString(oldConfigMap)
        connection.execSQL(
          """
          UPDATE streamer SET download_config = '$newConfig' WHERE id = $id
        """.trimIndent()
        )
      }
    }
  }

  private fun migrateToNewPluginActions(oldArray: JsonArray): JsonArray = buildJsonArray {
    oldArray.forEach {
      val jsonObject = it as JsonObject
      val type = jsonObject["type"]?.jsonPrimitive?.content
      val enabled = jsonObject["enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true
      when (type) {
        "rclone" -> {
          val newConfig = getNewRcloneConfig(jsonObject).also {
            it.enabled = enabled
          }
          add(json.encodeToJsonElement<PluginConfigs>(newConfig))
        }

        "remove" -> {
          val newConfig = PluginConfigs.DeleteFileConfig().also {
            it.enabled = enabled
          }
          add(json.encodeToJsonElement<PluginConfigs>(newConfig))
        }

        "copy" -> {
          val destination =
            jsonObject["destination"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("destination is null")
          val newConfig = PluginConfigs.CopyFileConfig(destination).also {
            it.enabled = enabled
          }
          add(json.encodeToJsonElement<PluginConfigs>(newConfig))
        }

        "move" -> {
          val destination =
            jsonObject["destination"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("destination is null")
          val newConfig = PluginConfigs.MoveFileConfig(destination).also {
            it.enabled = enabled
          }
          add(json.encodeToJsonElement<PluginConfigs>(newConfig))
        }

        "command" -> {
          val program =
            jsonObject["program"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("program is null")
          val args = jsonObject["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
          val newConfig = PluginConfigs.SimpleShellCommandConfig(
            commandTemplate = program,
            arguments = args
          ).also {
            it.enabled = enabled
          }
          add(json.encodeToJsonElement<PluginConfigs>(newConfig))
        }

        else -> {
          mainLogger.warn("migration(13,14) --- unknown plugin type: $type")
        }
      }

    }

  }


  private fun getNewRcloneConfig(oldJson: JsonObject): PluginConfigs.UploadConfig.RcloneConfig {
    // transform the old config to a new format
    /**
     * Example
     * {"type":"github.hua0512.data.upload.UploadConfig.RcloneConfig","platform":"RCLONE","rcloneOperation":"copy","remotePath":"onedrive2:records/{streamer}/%MM/%dd","args":["--config","/opt/records/rclone.conf","--onedrive-chunk-size","100M","--use-mmap","--no-traverse"]}
     */
    val path = oldJson["remotePath"]!!.jsonPrimitive.content
    val remote = path.substring(0, path.indexOf(":") + 1)
    val transferMode = oldJson["rcloneOperation"]!!.jsonPrimitive.content
    val args = oldJson["args"]!!.jsonArray.toMutableList()
    val enabled = oldJson["enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true
    var configIndex = -1
    args.forEachIndexed { index, value ->
      if (value.jsonPrimitive.content.contains("--config")) {
        configIndex = index
        return@forEachIndexed
      }
    }

    val configFile = if (configIndex == -1) {
      null
    } else {
      args[configIndex + 1].jsonPrimitive.content
      // remove --config and the next element
      args.removeAt(configIndex)
      args.removeAt(configIndex)
    }

    return PluginConfigs.UploadConfig.RcloneConfig(
      remoteName = remote,
      remotePath = path.substring(path.indexOf(":") + 1),
      transferMode = transferMode,
      configFile = configFile?.jsonPrimitive?.content,
      additionalFlags = args.map { it.jsonPrimitive.content },
    ).also {
      it.enabled = enabled
    }


  }

}