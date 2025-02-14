package github.hua0512.dao.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration12To13 : Migration(12, 13) {
  override fun migrate(connection: SQLiteConnection) {
    // Create the platform_configs table
    connection.execSQL("""
            CREATE TABLE IF NOT EXISTS platform_configs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                config_id INTEGER NOT NULL,
                platform_type TEXT NOT NULL,
                fetch_delay INTEGER,
                cookies TEXT,
                platform_settings TEXT NOT NULL,
                FOREIGN KEY(config_id) REFERENCES app_config(id) ON DELETE CASCADE
            )
        """)

    // Create index for faster lookups
    connection.execSQL("""
            CREATE INDEX IF NOT EXISTS index_platform_configs_config_id 
            ON platform_configs(config_id)
        """)

    // Migrate existing Huya configs
    connection.execSQL("""
            INSERT INTO platform_configs (config_id, platform_type, platform_settings)
            SELECT id, 'HUYA', huya_config
            FROM app_config
            WHERE huya_config IS NOT NULL
        """)

    // Migrate existing Douyin configs
    connection.execSQL("""
            INSERT INTO platform_configs (config_id, platform_type, platform_settings)
            SELECT id, 'DOUYIN', douyin_config
            FROM app_config
            WHERE douyin_config IS NOT NULL
        """)

    // Create temporary table for app_config
    connection.execSQL("""
            CREATE TABLE app_config_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                engine TEXT NOT NULL DEFAULT 'ffmpeg',
                danmu INTEGER NOT NULL DEFAULT 0,
                output_folder TEXT,
                output_file_name TEXT,
                output_file_format TEXT NOT NULL DEFAULT 'flv',
                min_part_size INTEGER NOT NULL,
                max_part_size INTEGER NOT NULL,
                max_part_duration INTEGER,
                max_download_retries INTEGER NOT NULL DEFAULT 3,
                download_retry_delay INTEGER NOT NULL DEFAULT 10,
                download_check_interval INTEGER NOT NULL DEFAULT 60,
                max_concurrent_downloads INTEGER NOT NULL DEFAULT 5,
                max_concurrent_uploads INTEGER NOT NULL DEFAULT 3,
                delete_files_after_upload INTEGER NOT NULL DEFAULT 0,
                use_built_in_segmenter INTEGER NOT NULL DEFAULT 0,
                exit_download_on_error INTEGER NOT NULL DEFAULT 0,
                enable_flv_fix INTEGER NOT NULL DEFAULT 0,
                enable_flv_duplicate_tag_filtering INTEGER NOT NULL DEFAULT 0,
                combine_ts_files INTEGER NOT NULL DEFAULT 0
            )
        """)

    // Copy data to new table
    connection.execSQL("""
            INSERT INTO app_config_new 
            SELECT id, engine, danmu, output_folder, output_file_name, output_file_format,
                   min_part_size, max_part_size, max_part_duration, max_download_retries,
                   download_retry_delay, download_check_interval, max_concurrent_downloads,
                   max_concurrent_uploads, delete_files_after_upload, use_built_in_segmenter,
                   exit_download_on_error, enable_flv_fix, enable_flv_duplicate_tag_filtering,
                   combine_ts_files
            FROM app_config
        """)

    // Drop old table and rename new one
    connection.execSQL("DROP TABLE app_config")
    connection.execSQL("ALTER TABLE app_config_new RENAME TO app_config")
  }
}