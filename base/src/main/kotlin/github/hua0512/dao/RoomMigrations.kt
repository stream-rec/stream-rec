/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.dao

import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import github.hua0512.data.stream.StreamerState
import github.hua0512.utils.md5

/**
 * This file contains all the room migrations
 * @author hua0512
 * @date : 2024/5/18 23:15
 */

@RenameColumn.Entries(
  RenameColumn(
    tableName = "app_config",
    fromColumnName = "pandaliveConfig",
    toColumnName = "pandaTvConfig"
  )
)
@DeleteColumn.Entries(
  DeleteColumn(
    tableName = "app_config",
    columnName = "pandaliveConfig"
  )
)
class Migrate1To2 : AutoMigrationSpec

object Migrate3To4 : Migration(3, 4) {

  override fun migrate(connection: SQLiteConnection) {
    connection.apply {
      // fetch all rows of user table
      val statement = prepare("SELECT * FROM user")
      statement.use {
        while (it.step()) {
          val idIndex = it.getColumnNames().indexOf("id")
          val passwordIndex = it.getColumnNames().indexOf("password")
          val id = it.getText(idIndex)
          val password = it.getText(passwordIndex)

          // hash password
          val hashedPassword = password.md5()

          // update password
          val updateStatement = prepare("UPDATE user SET password = ? WHERE id = ?")
          updateStatement.use {
            it.bindText(1, hashedPassword)
            it.bindText(2, id)
            it.step()
          }
        }
      }
    }
  }
}


object Migrate11To12 : Migration(11, 12) {

  override fun migrate(connection: SQLiteConnection) {
    with(connection) {
      val statement = prepare("SELECT * FROM streamer")
      // add state column
      val alterStatement = prepare("ALTER TABLE streamer ADD COLUMN state INTEGER NOT NULL DEFAULT 0")
      alterStatement.use {
        it.step()
      }

      statement.use {
        while (it.step()) {
          val idIndex = it.getColumnNames().indexOf("id")
          val isLiveIndex = it.getColumnNames().indexOf("is_live")
          val isActivatedIndex = it.getColumnNames().indexOf("is_active")
          val id = it.getLong(idIndex)
          val isLive = it.getBoolean(isLiveIndex)
          val isActive = it.getBoolean(isActivatedIndex)

          // add state column
          val state = if (!isActive) {
            StreamerState.CANCELLED
          } else if (isLive) {
            StreamerState.LIVE
          } else {
            StreamerState.NOT_LIVE
          }

          val updateStatement = prepare("UPDATE streamer SET state = ? WHERE id = ?")
          updateStatement.use {
            it.bindLong(1, state.value.toLong())
            it.bindLong(2, id)
            it.step()
          }
        }
      }

      // remove is_live and is_active columns
      arrayOf("is_live", "is_active").forEach { column ->
        val deleteStatement = prepare("ALTER TABLE streamer DROP COLUMN $column")
        deleteStatement.use {
          it.step()
        }
      }
    }
  }
}