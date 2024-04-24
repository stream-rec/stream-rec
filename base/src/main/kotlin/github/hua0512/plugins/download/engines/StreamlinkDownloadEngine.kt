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
import github.hua0512.data.stream.StreamData
import github.hua0512.logger
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.coroutines.coroutineContext

/**
 * Streamlink download engine for downloading streams using streamlink and ffmpeg
 * @author hua0512
 * @date : 2024/3/20 19:56
 */
class StreamlinkDownloadEngine() : BaseDownloadEngine() {

  private var streamlinkProcess: Process? = null
  private var ffmpegProcess: Process? = null

  private fun ensureHlsUrl(url: String): String {
    return if (url.contains("m3u8").not()) {
      throw UnsupportedOperationException("Streamlink download engine only supports HLS streams")
    } else {
      url
    }
  }

  override suspend fun startDownload(): StreamData? {
    // ensure download url is HLS
    ensureHlsUrl(downloadUrl!!)
    val streamlinkInputArgs = arrayOf("--stream-segment-threads", "3", "--hls-playlist-reload-attempts", "1")
    // streamlink headers
    val headersArray = headers.map {
      val (key, value) = it
      "--http-header" to "\"$key=$value\""
    }
    val headers = headersArray.flatMap { it.toList() }.run {
      if (cookies.isNullOrEmpty().not()) {
        this + listOf("--http-cookies", "\"$cookies\"")
      } else {
        this
      }
    }.toTypedArray()
    // streamlink args
    val streamlinkArgs = streamlinkInputArgs + headers + arrayOf(downloadUrl!!, "best", "-O")
    logger.info("Streamlink args: ${streamlinkArgs.joinToString(" ")}")
    val ffmpegCmdArgs = buildFFMpegCmd(emptyMap(), null, "pipe:0", downloadFormat!!, fileLimitSize, fileLimitDuration, downloadFilePath)
    logger.info("FFmpeg input args: ${ffmpegCmdArgs.joinToString(" ")}")

    // streamlink process builder
    val streamLinkBuilder = ProcessBuilder(App.streamLinkPath, *streamlinkArgs).apply {
      redirectInput(ProcessBuilder.Redirect.PIPE)
      redirectOutput(ProcessBuilder.Redirect.PIPE)
    }
    // ffmpeg process builder
    val ffmpegBuilder = ProcessBuilder(App.ffmpegPath, *ffmpegCmdArgs).apply {
      redirectInput(ProcessBuilder.Redirect.PIPE)
      redirectOutput(ProcessBuilder.Redirect.INHERIT)
    }
    val streamer = streamData!!.streamer

    val job = coroutineContext[Job.Key]
    return try {
      withIOContext {
        coroutineScope {
          val pipeList = withIOContext {
            ProcessBuilder.startPipeline(
              listOf(
                streamLinkBuilder,
                ffmpegBuilder,
              )
            )
          }
          streamlinkProcess = pipeList[0]
          ffmpegProcess = pipeList[1]
          job?.invokeOnCompletion {
            streamlinkProcess?.destroy()
          }
          // collect streamlink output
          launch {
            streamlinkProcess!!.errorStream.bufferedReader().forEachLine {
              // search for opening bracket
              if (it.contains("Opening")) {
                onDownloadStarted()
              }
              logger.info(it)
            }
          }
          // collect ffmpeg output
          launch {
            var downloaded = 0L
            ffmpegProcess!!.errorStream.bufferedReader().forEachLine {
              processFFmpegOutputLine(it, streamer = streamer.name, downloaded) { lastSize, diff, bitrate ->
                downloaded = lastSize
                onDownloadProgress(diff, bitrate)
              }
            }
          }

          // wait for ffmpeg process to finish
          val exitCode = ffmpegProcess!!.waitFor()
          if (exitCode == 0) {
            streamData!!.copy(
              dateStart = startTime.epochSeconds,
              dateEnd = Clock.System.now().epochSeconds,
              outputFilePath = downloadFilePath,
            )
          } else {
            logger.error("Download failed: FFmpeg process exited with code $exitCode")
            null
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      logger.error("Download failed: ${e.message}")
      null
    } finally {
      streamlinkProcess?.apply {
        destroy()
        waitFor()
      }
      ffmpegProcess?.apply {
        destroy()
        waitFor()
      }
      streamlinkProcess = null
      ffmpegProcess = null
    }
  }

  override suspend fun stopDownload(): Boolean {
    streamlinkProcess?.destroy()
    logger.info("(${streamData!!.streamer.name}) streamlink process stopped")
    val code = withIOContext {
      streamlinkProcess?.waitFor()
    }
    if (code != 0) {
      logger.error("streamlink process exited with code $code")
      return false
    }
    return true
  }
}