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

package github.hua0512.utils

import github.hua0512.logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/**
 * @author hua0512
 * @date : 2024/2/11 21:57
 */

fun Path.deleteFile(): Boolean = try {
  Files.deleteIfExists(this).also {
    if (it) logger.info("File deleted: {}", this)
    else logger.error("File not found: {}", this)
  }
} catch (e: Exception) {
  logger.error("Could not delete file: $this, $e")
  false
}

fun File.deleteFile(): Boolean = toPath().deleteFile()

fun Path.rename(newPath: Path, vararg options: CopyOption): Boolean {
  return try {
    // check existence of this file
    if (!Files.exists(this)) {
      logger.error("Error renaming $this, file do not exist")
      return false
    }

    // check existence of new file
    if (Files.exists(newPath)) {
      logger.error("Error renaming $this, new file already exists")
      return false
    }

    Files.move(this, newPath, *options).also {
      logger.info("File renamed: $this to $newPath")
    }
    true
  } catch (e: Exception) {
    logger.error("Could not rename file: $this to $newPath")
    false
  }
}

fun File.rename(newFile: File): Boolean = toPath().rename(newFile.toPath())

/**
 *
 */
fun decompressGzip(input: ByteArray): ByteArray {
  ByteArrayInputStream(input).use { bis ->
    GZIPInputStream(bis).use { gis ->
      ByteArrayOutputStream().use { bos ->
        val buffer = ByteArray(1024)
        var length: Int
        while (gis.read(buffer).also { length = it } > 0) {
          bos.write(buffer, 0, length)
        }
        return bos.toByteArray()
      }
    }
  }
}