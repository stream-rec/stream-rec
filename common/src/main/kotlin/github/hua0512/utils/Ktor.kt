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

import github.hua0512.download.DownloadProgressUpdater
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.io.readByteArray
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Writes the contents of a ByteReadChannel to a file.
 *
 * @param file The file to write to.
 * @param sizedUpdater A callback function to update the download statistics.
 * @param onDownloadComplete A callback function to be called when the download is complete.
 * @author hua0512
 * @date : 2024/9/18 22:06
 */
suspend fun ByteReadChannel.writeToFile(
  file: File,
  sizedUpdater: DownloadProgressUpdater = { _, _, _ -> },
  onDownloadComplete: () -> Unit = {},
) = withIOContext {
  val fos = file.outputStream().buffered()
  var downloaded = 0L
  var lastTime = Clock.System.now().toEpochMilliseconds()

  try {
    fos.use {
      while (!exhausted()) {
        val packet = readRemaining(DEFAULT_BUFFER_SIZE.toLong())
        val bytes = packet.readByteArray()
        fos.write(bytes)
        // update total downloaded bytes
        downloaded += bytes.size
        // update download statistics
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val timeDiff = currentTime - lastTime
        if (timeDiff > 1000) {
          val bitrate = (downloaded * 8.0 / timeDiff).roundToInt()
          sizedUpdater(downloaded, timeDiff.toFloat(), bitrate.toFloat())
        }
      }
    }
    true
  } catch (e: Exception) {
    mainLogger.error("writeToFile error: ${e.message}")
    throw e
  } finally {
    onDownloadComplete()
  }
}

suspend fun ByteReadChannel.writeToOutputStream(outputStream: OutputStream) = withContext(Dispatchers.IO) {
  outputStream.use { os ->

    while (!exhausted()) {
      val packet = readRemaining(DEFAULT_BUFFER_SIZE.toLong())
      val bytes = packet.readByteArray()
      os.write(bytes)
    }

  }
}

