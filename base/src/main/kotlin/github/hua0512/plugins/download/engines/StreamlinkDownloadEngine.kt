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
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * A download engine that uses streamlink and pipe the output to ffmpeg for downloading streams
 * @author hua0512
 * @date : 2024/5/7 13:46
 */
class StreamlinkDownloadEngine : FFmpegDownloadEngine() {

  companion object {
    private val logger = LoggerFactory.getLogger("Streamlink")
  }

  private var streamlinkProcess: Process? = null

  override suspend fun start() = coroutineScope {
    ensureHlsUrl()
    initPath()
    val streamlinkInputArgs = arrayOf("--stream-segment-threads", "3", "--hls-playlist-reload-attempts", "1")
    // streamlink headers
    val headersArray = headers.map {
      val (key, value) = it
      "--http-header" to "\"$key=$value\""
    }.toMutableList()

    if (cookies.isNullOrEmpty().not()) {
      val separatedCookies = cookies!!.split(";").map { it.trim() }
      val cookiesArray = separatedCookies.map {
        // --http-cookie KEY=VALUE
        "--http-cookie" to "\'$it\'"
      }
      headersArray += cookiesArray
    }
    val headers = headersArray.flatMap { it.toList() }.toTypedArray()
    val streamer = streamer ?: throw IllegalArgumentException("Streamer is not set")
    // streamlink args
    val streamlinkArgs = streamlinkInputArgs + headers + arrayOf(downloadUrl!!, "best", "-O")
    logger.debug("${streamer.name} streamlink command: ${streamlinkArgs.joinToString(" ")}")
    val ffmpegCmdArgs =
      buildFFMpegCmd(emptyMap(), null, "pipe:0", downloadFormat!!, fileLimitSize, fileLimitDuration, useSegmenter, outputFileName)
    logger.debug("${streamer.name} ffmpeg command: ${ffmpegCmdArgs.joinToString(" ")}")
    // streamlink process builder
    val streamLinkBuilder = ProcessBuilder(App.streamLinkPath, *streamlinkArgs).apply {
      redirectInput(ProcessBuilder.Redirect.PIPE)
      redirectOutput(ProcessBuilder.Redirect.PIPE)
      directory(outputFolder.toFile())
    }

    val ffmpegBuilder = ProcessBuilder(App.ffmpegPath, *ffmpegCmdArgs).apply {
      redirectInput(ProcessBuilder.Redirect.PIPE)
      redirectErrorStream()
      directory(outputFolder.toFile())
    }

    if (!useSegmenter) {
      onDownloadStarted(downloadFilePath, Clock.System.now().epochSeconds)
    }
    // process pipeline
    val pipes = withIOContext {
      ProcessBuilder.startPipeline(
        listOf(
          streamLinkBuilder,
          ffmpegBuilder
        )
      )
    }
    // get streamlink process
    streamlinkProcess = pipes.first()
    // get streamlink output
    launch(Dispatchers.IO) {
      streamlinkProcess?.errorReader()?.forEachLine {
        logger.info("${streamer.name} $it")
      }
    }
    // get ffmpeg process
    process = pipes.last()
    // get ffmpeg output
    launch(Dispatchers.IO) {
      // get error stream
      while (process?.isAlive == true) {
        val line = process?.errorStream?.bufferedReader()?.readLine() ?: break
        processFFmpegOutputLine(
          line = line,
          streamer = streamer.name,
          lastSize = lastOpeningSize,
          onSegmentStarted = { name ->
            processSegment(outputFolder, name)
          }
        ) { size, diff, bitrate ->
          handleDownloadProgress(bitrate, size, diff)
        }
      }
    }

    val exitCode = process?.waitFor() ?: -1

    handleExitCodeAndStreamer(exitCode, streamer)
    streamlinkProcess = null
    process = null
  }

  override suspend fun stop(): Boolean {
    sendStopSignal()
    val exitCode = withIOContext {
      streamlinkProcess?.waitFor()
    }
    return exitCode == 0
  }

  override fun sendStopSignal() {
    // stop streamlink
    streamlinkProcess?.destroy()
  }


  private fun ensureHlsUrl() {
    require(downloadUrl!!.contains("m3u8")) {
      "Streamlink download engine only supports HLS streams"
    }
  }
}