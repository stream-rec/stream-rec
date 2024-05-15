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
import github.hua0512.logger
import github.hua0512.utils.executeProcess
import github.hua0512.utils.process.InputSource
import github.hua0512.utils.process.Redirect
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.io.path.Path

/**
 * A download engine that uses streamlink and pipe the output to ffmpeg for downloading streams
 * @author hua0512
 * @date : 2024/5/7 13:46
 */
class StreamlinkDownloadEngine : FFmpegDownloadEngine() {

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
    logger.info("${streamer.name} streamlink command: ${streamlinkArgs.joinToString(" ")}")
    val ffmpegCmdArgs =
      buildFFMpegCmd(emptyMap(), null, "pipe:0", downloadFormat!!, fileLimitSize, fileLimitDuration, useSegmenter, outputFileName)
    logger.info("${streamer.name} ffmpeg command: ${ffmpegCmdArgs.joinToString(" ")}")
    // streamlink process builder
    val streamLinkBuilder = ProcessBuilder(App.streamLinkPath, *streamlinkArgs).apply {
      redirectInput(ProcessBuilder.Redirect.PIPE)
      redirectOutput(ProcessBuilder.Redirect.PIPE)
      directory(outputFolder.toFile())
    }
    streamlinkProcess = withIOContext {
      streamLinkBuilder.start()
    }

    launch {
      streamlinkProcess?.errorReader()?.forEachLine {
        logger.info("${streamer.name} $it")
      }
    }
    if (!useSegmenter) {
      onDownloadStarted(downloadFilePath, Clock.System.now().epochSeconds)
    }

    val exitCode = executeProcess(
      App.ffmpegPath,
      *ffmpegCmdArgs,
      directory = Path(downloadFilePath).parent.toFile(),
      stdin = InputSource.fromInputStream(streamlinkProcess!!.inputStream, closeInput = false),
      stdout = Redirect.CAPTURE,
      stderr = Redirect.CAPTURE,
      destroyForcibly = false,
      getOutputStream = {
        ous = it
      },
      getProcess = {
        process = it
      }) { line ->
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

    handleExitCodeAndStreamer(exitCode, streamer)
    streamlinkProcess?.destroy()
    process?.destroy()
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
    streamlinkProcess?.destroy()
    process?.destroy()
  }


  private fun ensureHlsUrl() {
    require(downloadUrl!!.contains("m3u8")) {
      "Streamlink download engine only supports HLS streams"
    }
  }
}