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

package github.hua0512.plugins.download.engines.ffmpeg

import github.hua0512.app.Programs.ffmpeg
import github.hua0512.app.Programs.streamLink
import github.hua0512.utils.debug
import github.hua0512.utils.error
import github.hua0512.utils.executeProcess
import github.hua0512.utils.info
import github.hua0512.utils.isWindows
import github.hua0512.utils.nonEmptyOrNull
import github.hua0512.utils.process.InputSource
import github.hua0512.utils.process.Redirect
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.Path

/**
 * A download engine that uses streamlink and pipe the output to ffmpeg for downloading streams
 * @author hua0512
 * @date : 2024/5/7 13:46
 */
class StreamlinkDownloadEngine(override val logger: Logger = StreamlinkDownloadEngine.logger) :
  FFmpegDownloadEngine() {

  companion object {
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  private var streamlinkProcess: Process? = null

  override suspend fun start() = coroutineScope {
    ensureHlsUrl()
    initPath(Clock.System.now())
    val streamlinkInputArgs =
      mutableListOf("--stream-segment-threads", "3", "--hls-playlist-reload-attempts", "1").apply {
        // add program args
        if (programArgs.isNotEmpty()) {
          addAll(programArgs)
        }
        // check if windows
        val isWindows = isWindows()
        // add headers
        headers.forEach {
          val (key, value) = it
          add("--http-header")
          // check if windows
          if (isWindows) {
            add("\"$key=$value\"")
          } else {
            add("$key=$value")
          }
        }
        // add cookies if any
        if (cookies?.nonEmptyOrNull() != null) {
          val separatedCookies = cookies!!.split(";").map { it.trim() }
          separatedCookies.forEach {
            if (it.nonEmptyOrNull() == null) return@forEach
            add("--http-cookie")
            add(it)
          }
        }
      }

    // streamlink args
    val streamlinkArgs = streamlinkInputArgs.toTypedArray() + arrayOf(downloadUrl!!, "best", "-O")
    debug("streamlink command: ${streamlinkArgs.joinToString(" ")}")
    val ffmpegCmdArgs = buildFFMpegCmd(
      emptyMap(),
      null,
      "pipe:0",
      downloadFormat!!,
      fileLimitSize,
      fileLimitDuration,
      useSegmenter,
      detectErrors,
      outputFileName
    )
    debug("ffmpeg command: ${ffmpegCmdArgs.joinToString(" ")}")
    // streamlink process builder
    val streamLinkBuilder = ProcessBuilder(streamLink, *streamlinkArgs).apply {
      redirectInput(ProcessBuilder.Redirect.PIPE)
      redirectOutput(ProcessBuilder.Redirect.PIPE)
      directory(outputFolder.toFile())
    }

    // get streamlink process
    streamlinkProcess = withIOContext {
      streamLinkBuilder.start()
    }
    // get streamlink output
    launch(Dispatchers.IO) {
      try {
        streamlinkProcess?.errorReader()?.forEachLine {
          info(it)
        }
      } catch (e: Exception) {
        error("Error reading streamlink output", throwable = e)
      }
    }
    if (!useSegmenter) {
      onDownloadStarted(downloadFilePath, Clock.System.now().epochSeconds)
    }

    // listen for streamlink exit
    streamlinkProcess!!.onExit().thenApply {
      debug("streamlink exited({})", { it.exitValue() })
      super.sendStopSignal()
    }

    val exitCode = executeProcess(
      ffmpeg,
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
      },
      onCancellation = {
        streamlinkProcess?.destroy()
      }) { line ->
      processFFmpegOutputLine(
        line = line,
        streamer = context.name,
        lastSize = lastOpeningSize,
        onSegmentStarted = { name ->
          processSegment(outputFolder, name)
        }
      ) { size, diff, bitrate ->
        handleDownloadProgress(bitrate, size, diff)
      }
    }

    handleExitCode(exitCode)
    // ensure the streamlink process is destroyed
    streamlinkProcess?.destroy()
    streamlinkProcess = null
    process = null
  }

  override suspend fun stop(exception: Exception?): Boolean {
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
    if (programArgs.contains("--twitch-disable-ads") || programArgs.firstOrNull { it.startsWith("--twitch-proxy-playlist") } != null) {
      return
    }
    require(downloadUrl!!.contains("m3u8")) {
      "Streamlink download engine only supports HLS streams"
    }
  }
}