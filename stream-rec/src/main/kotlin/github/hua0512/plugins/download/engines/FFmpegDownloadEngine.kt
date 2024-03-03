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
import github.hua0512.data.VideoFormat
import github.hua0512.data.stream.StreamData
import github.hua0512.utils.executeProcess
import github.hua0512.utils.process.Redirect
import github.hua0512.utils.withIOContext
import io.ktor.http.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*

/**
 * FFmpegDownloadEngine is a download engine that uses ffmpeg to download the stream.
 * @author hua0512
 * @date : 2024/2/12 18:22
 */
class FFmpegDownloadEngine(
  override val app: App,
) : BaseDownloadEngine(app = app) {

  companion object {
    @JvmStatic
    private val logger = LoggerFactory.getLogger(FFmpegDownloadEngine::class.java)
  }

  override suspend fun startDownload(): StreamData? {
    val segmentPart = app.config.maxPartSize.run {
      if (this > 0) this else 2621440000
    }
    downloadFormat = downloadFormat ?: extractFormatFromPath(downloadFilePath)
    val segmentTime = app.config.maxPartDuration

    // ffmpeg input args
    val defaultFFmpegInputArgs = buildDefaultFFMpegInputArgs(cookies)

    // default output args
    val defaultFFmpegOutputArgs = mutableListOf<String>().apply {
      if (downloadFormat == VideoFormat.avi) {
        add("-bsf:v")
        add("h264_mp4toannexb")
      }
      addAll(
        arrayOf(
          "-bsf:a",
          "aac_adtstoasc",
          "-fflags",
          "+discardcorrupt",
        )
      )
      // segment the file, according to the maxPartDuration
      if (segmentTime != null) {
        add("-to")
        add(segmentTime.toString())
      } else { // segment the file, according to the maxPartSize
        add("-fs")
        add(segmentPart.toString())
      }
    }

    // ffmpeg running commands
    val cmds = buildFFMpegRunningCmd(defaultFFmpegInputArgs, defaultFFmpegOutputArgs.toTypedArray()) + downloadFilePath

    val streamer = streamData!!.streamer

    return withIOContext {
      logger.info("(${streamer.name}) Starting download using ffmpeg...")
      onDownloadStarted()
      // last size of the file
      var lastSize = 0L
      val exitCode = executeProcess(app.ffmepgPath, *cmds, stdout = Redirect.CAPTURE, stderr = Redirect.CAPTURE, destroyForcibly = true) { line ->
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

      logger.info("(${streamer.name}) download finished, exit code: $exitCode")
      if (exitCode != 0) {
        logger.error("(${streamer.name}) download failed, exit code: $exitCode")
        null
      } else {
        // case when download is successful (exit code is 0)
        streamData!!.copy(
          dateStart = startTime.epochSeconds,
          dateEnd = Clock.System.now().epochSeconds,
          outputFilePath = downloadFilePath.removeSuffix(".part"),
        )
      }
    }
  }

  private fun extractFormatFromPath(downloadFilePath: String): VideoFormat? {
    val extension = downloadFilePath.substringAfterLast(".").lowercase(Locale.getDefault())
    return VideoFormat.format(extension)
  }

  private fun buildFFMpegRunningCmd(
    defaultFFmpegInputArgs: List<String>,
    defaultFFmpegOutputArgs: Array<String>,
  ) = arrayOf(
    "-y"
  ) + defaultFFmpegInputArgs + arrayOf(
    "-i",
    downloadUrl!!,
  ) + defaultFFmpegOutputArgs + arrayOf(
    "-c",
    "copy",
    "-f",
    downloadFormat!!.ffmpegMuxer,
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