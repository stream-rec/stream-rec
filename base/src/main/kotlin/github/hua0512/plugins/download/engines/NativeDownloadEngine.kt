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

package github.hua0512.plugins.download.engines

import github.hua0512.data.stream.StreamData
import github.hua0512.logger
import github.hua0512.utils.withIOContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Native download engine, uses Ktor client to download the stream
 * @author hua0512
 * @date : 2024/2/12 18:45
 */
class NativeDownloadEngine(val client: HttpClient) : BaseDownloadEngine() {

  override suspend fun startDownload(): StreamData? {
    val outputFile = Path.of(downloadFilePath).toFile()

    return try {
      withIOContext {
        Files.createFile(outputFile.toPath())
      }

      withIOContext {
        client.prepareGet(downloadUrl!!) {
          headers {
            this@NativeDownloadEngine.headers.forEach { (t, u) ->
              append(t, u)
            }
            append("keep-alive", "true")
          }
        }.execute { response ->
          val channel: ByteReadChannel = response.bodyAsChannel()
          while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
            while (!packet.isEmpty) {
              val bytes = packet.readBytes()
              if (outputFile.length() + bytes.size > fileLimitSize) {
                logger.error("File size limit reached")
                return@execute
              }
              Files.write(outputFile.toPath(), bytes, StandardOpenOption.APPEND)
            }
          }
        }
      }
      streamData?.copy(
        dateStart = startTime.epochSeconds,
        dateEnd = Clock.System.now().epochSeconds,
        outputFilePath = downloadFilePath,
      )
    } catch (e: Exception) {
      logger.error("Error downloading stream", e)
      return null
    }
  }

  override suspend fun stopDownload(): Boolean {
    TODO("Not yet implemented")
  }
}