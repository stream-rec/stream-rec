/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
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

package github.hua0512.repo

import github.hua0512.data.config.AppConfig
import github.hua0512.logger
import github.hua0512.utils.generateRandomString
import kotlinx.coroutines.flow.Flow
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString

/**
 * Local data source
 * @author hua0512
 * @date : 2024/2/18 23:50
 */
interface LocalDataSource {

  companion object {
    fun getDefaultPath(): String {
      val envPath = System.getenv("DB_PATH") ?: System.getProperty("user.dir")
      val path = Path(envPath, "db").resolve("stream-rec.db")
      return path.pathString
    }

    fun getJwtSecret(): String {
      val config = Path(getDefaultPath()).resolveSibling("config.conf").also {
        if (!Files.exists(it)) {
          // create config file
          Files.createFile(it)
        }
      }
      val configContent = Files.readAllLines(config)
      return configContent.find { it.startsWith("jwt.secret") }?.split("=")?.get(1)?.trim().run {
        if (isNullOrBlank()) {
          val generated = generateRandomString(32, true)
          logger.info("First run, generate jwt secret $generated")
          // append jwt secret to end of file
          Files.writeString(config, "\njwt.secret = $generated", Charsets.UTF_8, StandardOpenOption.APPEND)
          generated
        } else return this
      }
    }

    fun isFirstRun(): Boolean {
      val dbPath = getDefaultPath()
      return !Files.exists(Path(dbPath))
    }

    @Deprecated("Remove in next version")
    private fun getDbVersionPath(): String {
      // get db version from file
      val dbPath = getDefaultPath()
      return Path(dbPath).resolveSibling("version").pathString
    }

    @Deprecated("Remove in next version")
    private fun getDbTypePath(): String {
      // get db version from file
      val dbPath = getDefaultPath()
      return Path(dbPath).resolveSibling("type").pathString
    }

    @Deprecated("Remove in next version")
    fun getDbVersion(): Int {
      val dbVersionPath = Path(getDbVersionPath())
      // check if db version file exists
      if (!Files.exists(dbVersionPath)) return 0
      return Files.readString(dbVersionPath, Charsets.UTF_8).toIntOrNull() ?: 0
    }

    @Deprecated("Remove in next version")
    fun writeDbVersion(version: Int) {
      val dbVersionPath = Path(getDbVersionPath())
      dbVersionPath.createParentDirectories()
      // overwrite db version file
      Files.writeString(dbVersionPath, version.toString(), Charsets.UTF_8)
    }

    @Deprecated("Remove in next version")
    fun getDbType(): String {
      val dbTypePath = Path(getDbTypePath())
      // check if db type file exists
      if (!Files.exists(dbTypePath)) return "sqldelight"
      return Files.readString(dbTypePath, Charsets.UTF_8)
    }

    @Deprecated("Remove in next version")
    fun writeDbType(type: String) {
      val dbTypePath = Path(getDbTypePath())
      dbTypePath.createParentDirectories()
      // overwrite db type file
      Files.writeString(dbTypePath, type, Charsets.UTF_8)
    }

  }

  suspend fun streamAppConfig(): Flow<AppConfig>

  suspend fun getAppConfig(): AppConfig

  fun getPath(): String

  suspend fun saveAppConfig(appConfig: AppConfig)
}