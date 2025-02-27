package github.hua0512.dao

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import github.hua0512.data.config.engine.EngineConfig
import kotlinx.serialization.json.Json

class Migrate12To13(val json: Json) : Migration(12, 13) {
  override fun migrate(connection: SQLiteConnection) {
    // create new table engine_config
    connection.execSQL(
      """
            CREATE TABLE IF NOT EXISTS engine_config (`config_id` INTEGER NOT NULL, `engine_type` TEXT NOT NULL, `config` TEXT NOT NULL, PRIMARY KEY(`engine_type`), FOREIGN KEY(`config_id`) REFERENCES `app_config`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
          """.trimIndent()
    )

    // fetch relevant fields from the old table APP_CONFIG
    val oldConfigStatement = connection.prepare(
      """
      SELECT id, useBuiltInSegmenter, exitDownloadOnError, enableFlvFix, enableFlvDuplicateTagFiltering, combineTsFiles FROM app_config
    """.trimIndent()
    )

    oldConfigStatement.use {
      while (oldConfigStatement.step()) {
        val id = oldConfigStatement.getLong(0)
        val useBuiltInSegmenter = oldConfigStatement.getLong(1)
        val exitDownloadOnError = oldConfigStatement.getLong(2)
        val enableFlvFix = oldConfigStatement.getLong(3)
        val enableFlvDuplicateTagFiltering = oldConfigStatement.getLong(4)
        val combineTsFiles = oldConfigStatement.getLong(5)

        // new configs
        val ffmpegConfig = EngineConfig.FFmpegConfig(
          useBuiltInSegmenter = useBuiltInSegmenter == 1L,
          exitDownloadOnError = exitDownloadOnError == 1L
        )

        val ktConfig = EngineConfig.KotlinConfig(
          enableFlvFix = enableFlvFix == 1L,
          enableFlvDuplicateTagFiltering = enableFlvDuplicateTagFiltering == 1L,
          combineTsFiles = combineTsFiles == 1L
        )

        val streamlinkConfig = EngineConfig.StreamlinkConfig(
          useBuiltInSegmenter = useBuiltInSegmenter == 1L,
          exitDownloadOnError = exitDownloadOnError == 1L
        )

        // insert new configs into the new table ENGINE_CONFIG
        connection.execSQL(
          """
            INSERT INTO engine_config(config_id, engine_type, config) VALUES($id, 'ffmpeg', '${
            json.encodeToString<EngineConfig>(
              ffmpegConfig
            )
          }')
          """.trimIndent()
        )
        connection.execSQL(
          """
            INSERT INTO engine_config(config_id, engine_type, config) VALUES($id, 'kotlin', '${
            json.encodeToString<EngineConfig>(
              ktConfig
            )
          }')
          """.trimIndent()
        )
        connection.execSQL(
          """
            INSERT INTO engine_config(config_id, engine_type, config) VALUES($id, 'streamlink', '${
            json.encodeToString<EngineConfig>(
              streamlinkConfig
            )
          }')
          """.trimIndent()
        )

      }
    }

    // DROP app_config respective columns
    connection.execSQL(
      """
            ALTER TABLE app_config DROP COLUMN useBuiltInSegmenter
          """.trimIndent()
    )
    connection.execSQL(
      """
            ALTER TABLE app_config DROP COLUMN exitDownloadOnError
          """.trimIndent()
    )
    connection.execSQL(
      """
            ALTER TABLE app_config DROP COLUMN enableFlvFix
          """.trimIndent()
    )
    connection.execSQL(
      """
            ALTER TABLE app_config DROP COLUMN enableFlvDuplicateTagFiltering
          """.trimIndent()
    )
    connection.execSQL(
      """
            ALTER TABLE app_config DROP COLUMN combineTsFiles
          """.trimIndent()
    )

    // add new columns to streamers
    connection.execSQL(
      """
        ALTER TABLE streamer ADD COLUMN engine TEXT
      """.trimIndent()
    )
    connection.execSQL(
      """
        ALTER TABLE streamer ADD COLUMN engine_config TEXT
      """.trimIndent()
    )
  }
}