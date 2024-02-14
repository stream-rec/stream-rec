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

import github.hua0512.app.App
import github.hua0512.data.StreamData
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume

/**
 * FFmpegDownloadEngine is a download engine that uses ffmpeg to download the stream.
 * @author hua0512
 * @date : 2024/2/12 18:22
 */
class FFmpegDownloadEngine(
  override val app: App,
  override var onDownloadStarted: () -> Unit = {},
  override var onDownloadProgress: (size: Long, bitrate: String) -> Unit = { _, _ -> },
) : BaseDownloadEngine(app = app) {

  companion object {
    @JvmStatic
    private val logger = LoggerFactory.getLogger(FFmpegDownloadEngine::class.java)
  }

  override suspend fun startDownload(): StreamData? {
    val segmentPart = app.config.maxPartSize.run {
      if (this > 0) this else 2621440000
    }

    // ffmpeg input args
    val defaultFFmpegInputArgs = buildDefaultFFMpegInputArgs(cookies)

    val defaultFFmpegOutputArgs = arrayOf(
      "-bsf:a",
      "aac_adtstoasc",
      "-fs",
      segmentPart.toString(),
    )

    val fileExtension = downloadFilePath.removeSuffix(".part").substringAfterLast(".")

    // ffmpeg running commands
    val cmds = buildFFMpegRunningCmd(defaultFFmpegInputArgs, defaultFFmpegOutputArgs, fileExtension) + downloadFilePath

    val streamer = streamData!!.streamer

    return withContext(Dispatchers.IO) {
      val builder = ProcessBuilder(*cmds)
      logger.info("(${streamer.name}) Starting download using ffmpeg...")
      // use suspendCancellableCoroutine to handle process cancellation
      val exitCode = suspendCancellableCoroutine { continuation ->
        val process = builder
          .redirectErrorStream(true)
          .start()
        onDownloadStarted()
        // handle process cancellation
        continuation.invokeOnCancellation {
          process.destroy()
          logger.info("(${streamer.name}) download process is cancelled : $it")
        }
        launch {
          var lastSize = 0L
          while (process.isAlive) {
            process.inputStream.bufferedReader().readLine()?.let { line ->
              if (!line.startsWith("size="))
                logger.info("${streamer.name} - $line")
              else {
                //  size=     768kB time=00:00:02.70 bitrate=2330.2kbits/s speed=5.28x
                val sizeString = line.substringAfter("size=").substringBefore("time").trim()
                // extract the size in kB
                val size = sizeString.replace(Regex("[^0-9]"), "").toLong()
                val diff = size - lastSize
                lastSize = size
                val bitrate = line.substringAfter("bitrate=").substringBefore("speed").trim()
                onDownloadProgress(diff, bitrate)
              }
            }
          }
          logger.info("(${streamer.name}) - ffmpeg process is finished")
        }

        continuation.resume(process.waitFor())
      }

      logger.info("(${streamer.name}) download finished, exit code: $exitCode")
      if (exitCode != 0) {
        logger.error("(${streamer.name}) download failed, exit code: $exitCode")
        null
      } else {
        // case when download is successful (exit code is 0)
        streamData!!.copy(
          dateStart = startTime,
          dateEnd = System.currentTimeMillis(),
          outputFilePath = downloadFilePath.removeSuffix(".part"),
        )
      }
    }
  }

  private fun buildFFMpegRunningCmd(
    defaultFFmpegInputArgs: List<String>,
    defaultFFmpegOutputArgs: Array<String>,
    fileExtension: String,
  ) = arrayOf(
    app.ffmepgPath,
    "-y"
  ) + defaultFFmpegInputArgs + arrayOf(
    "-i",
    downloadUrl!!,
  ) + defaultFFmpegOutputArgs + arrayOf(
    "-c",
    "copy",
    "-f",
    fileExtension.removeSuffix(".part"),
  )

  private fun buildDefaultFFMpegInputArgs(cookies: String? = null) = mutableListOf<String>().apply {
    headers.forEach {
      val prefix = if (it.key == HttpHeaders.UserAgent) "-user_agent" else "-headers"
      add(prefix)
      add("${it.key}: ${it.value}")
    }
    // ensure that the headers are properly separated
    add("-headers")
    add("\r\n")
    // add cookies if available
    if (cookies.isNullOrEmpty().not()) {
      add("-cookies")
      add(cookies!!)
    }
    addAll(
      arrayOf(
        "-rw_timeout",
        "20000000",
      )
    )

    if (downloadUrl!!.contains(".m3u8"))
      addAll(
        arrayOf(
          "-max_reload",
          "1000",
        )
      )
  }
}