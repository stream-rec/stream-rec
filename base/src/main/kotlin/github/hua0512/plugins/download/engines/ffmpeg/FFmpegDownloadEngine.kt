/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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
import github.hua0512.app.Programs.ffprobe
import github.hua0512.data.stream.FileInfo
import github.hua0512.download.exceptions.DownloadErrorException
import github.hua0512.flv.data.video.VideoResolution
import github.hua0512.plugins.download.engines.BaseDownloadEngine
import github.hua0512.utils.*
import github.hua0512.utils.process.Redirect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*

/**
 * FFmpegDownloadEngine is a download engine that uses ffmpeg to download the stream.
 * @author hua0512
 * @date : 2024/5/5 21:16
 */
open class FFmpegDownloadEngine(override val logger: Logger = Companion.logger) :
  BaseDownloadEngine() {

  companion object {
    @JvmStatic
    internal val logger = logger(FFmpegDownloadEngine::class.java)
  }

  /**
   * Whether to use ffmpeg built-in segmenter to download the stream
   */
  internal var useSegmenter: Boolean = false
  internal var detectErrors: Boolean = false

  protected var ous: OutputStream? = null
  protected var process: Process? = null
  protected var ffprobeProcess: Process? = null

  protected var lastOpeningFile: String? = null
  protected var lastOpeningFileTime: Long = 0
  protected var lastOpeningSize = 0L
  protected lateinit var outputFolder: Path
  protected lateinit var outputFileName: String

  private val resulutionSet = mutableSetOf<VideoResolution>()

  protected fun initPath(startInstant: Instant) {
    updateOutputFolder(startInstant)
    outputFileName = Path(downloadFilePath).name
    if (!useSegmenter) {
      lastOpeningFileTime = startInstant.epochSeconds
      // replace time placeholders if not using segmenter
      outputFileName = outputFileName.replacePlaceholders(context.name, context.title, context.platform, startInstant)
      // update downloadFilePath
      downloadFilePath = outputFolder.resolve(outputFileName).pathString
      lastOpeningFile = outputFileName
    }
  }

  private fun updateOutputFolder(startInstant: Instant) {
    outputFolder = Path(downloadFilePath.replacePlaceholders(context.name, context.title, context.platform, startInstant)).parent
    outputFolder.createDirectories()
  }

  override suspend fun start() = coroutineScope {
    val startTime = Clock.System.now()
    initPath(startTime)
    // ffmpeg running commands
    val cmds = buildFFMpegCmd(
      headers,
      cookies,
      downloadUrl!!,
      downloadFormat!!,
      fileLimitSize,
      fileLimitDuration,
      useSegmenter,
      false,
      outputFileName
    )

    val streamer = context
    debug("ffmpeg command: ${cmds.joinToString(" ")}")
    if (!useSegmenter) {
      onDownloadStarted(downloadFilePath, startTime.epochSeconds)
    }

    if (detectErrors)
      launch {
        // detect errors by using ffprobe
        // source : https://superuser.com/questions/841235/how-do-i-use-ffmpeg-to-get-the-video-resolution
        // I doubt this will work for all streams, but it's worth a try
        val cmds = buildFFprobeCmd(headers, cookies, downloadUrl!!)
        val exitCode = executeProcess(
          ffprobe,
          *cmds,
          stdout = Redirect.CAPTURE,
          stderr = Redirect.CAPTURE,
          destroyForcibly = false,
          getProcess = {
            ffprobeProcess = it
          },
        ) { line ->
          if (line.contains("x")) {
            val res = line.split("x")
            if (res.size == 2) {
              val resolution = VideoResolution(res[0].toInt(), res[1].toInt())
              val result = resulutionSet.add(resolution)
              if (result) {
                debug("resolution detected: {}", resolution)

                if (resulutionSet.size > 1) {
                  error("resolution changed: {}", resolution)
                  sendStopSignal()
                  ffprobeProcess?.destroy()
                  ffprobeProcess = null
                }
              }
            }
          }
        }
      }

    val exitCode: Int = executeProcess(
      ffmpeg,
      *cmds,
      directory = outputFolder.toFile(),
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
        sendStopSignal()
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
    handleExitCode(exitCode)
    process = null
    ffprobeProcess = null
    ous = null
    resulutionSet.clear()
  }

  protected fun handleDownloadProgress(
    bitrate: String,
    size: Long,
    diff: Long,
  ) {
    // if using segmentation, ffmpeg does not provide the total size of the file
    if (useSegmenter) {
      // calculate the total size of the file
      val currentSize = lastOpeningFile?.let { outputFolder.resolve(it).fileSize() } ?: 0
      val newDiff = currentSize - lastOpeningSize
      trace(
        "currentSize: {}, lastPartedSize: {}, diff: {}, bitrate: {}",
        currentSize,
        lastOpeningSize,
        newDiff,
        bitrate
      )
      lastOpeningSize = currentSize
      val bitrate = getBitrate(bitrate)
      onDownloadProgress(newDiff, bitrate)
    } else {
      lastOpeningSize = size
      val bitrate = getBitrate(bitrate)
      onDownloadProgress(diff, bitrate)
    }
  }

  protected fun handleExitCode(exitCode: Int) {
    if (lastOpeningFile == null) {
      error("ffmpeg download failed, exit code: {}", exitCode)
      onDownloadError(downloadFilePath, DownloadErrorException("ffmpeg download failed (exit code: $exitCode)"))
      return
    }
    val file = outputFolder.resolve(lastOpeningFile!!)
    if (exitCode != 0) {
      error("ffmpeg download failed, exit code: $exitCode")
      // check if the file exists
      if (file.exists()) {
        onDownloaded(FileInfo(file.pathString, 0, lastOpeningFileTime, Clock.System.now().epochSeconds))
        onDownloadFinished()
      } else {
        onDownloadError(file.pathString, DownloadErrorException("ffmpeg download failed, file not created"))
      }
    } else {
      // case when download is successful (exit code is 0)
      onDownloaded(FileInfo(file.pathString, 0, lastOpeningFileTime, Clock.System.now().epochSeconds))
      onDownloadFinished()
    }

    // delete 'core' file (ffmpeg error file) if it exists as we don't need it
    val coreFile = outputFolder.resolve("core")
    if (coreFile.exists()) {
      coreFile.deleteFile()
    }
  }


  protected fun processSegment(folder: Path, fileName: String) {
    // first segment
    if (lastOpeningFile == null) {
      debug("first segment: {}", fileName)
      lastOpeningFile = fileName
      val now = Clock.System.now().epochSeconds
      lastOpeningFileTime = now
      onDownloadStarted(folder.resolve(fileName).pathString, now)
      return
    }
    val now = Clock.System.now()
    val nowEpoch = now.epochSeconds
    updateOutputFolder(now)
    debug("segment finished: {}", lastOpeningFile)
    // construct file data
    val fileData = FileInfo(
      path = folder.resolve(lastOpeningFile!!).pathString,
      size = lastOpeningSize,
      createdAt = lastOpeningFileTime,
      updatedAt = nowEpoch
    )
    // notify last segment finished
    onDownloaded(fileData)
    // notify segment started
    debug("segment started: {}", fileName)
    // reset lastOpeningSize
    lastOpeningSize = 0
    lastOpeningFile = fileName
    lastOpeningFileTime = nowEpoch
    onDownloadStarted(folder.resolve(fileName).pathString, nowEpoch)
  }


  protected fun getBitrate(bitrate: String): Double = try {
    bitrate.substring(0, bitrate.indexOf("k")).toDouble()
  } catch (e: Exception) {
    0.0
  }


  protected open fun sendStopSignal() {
    if (ous == null) return
    // check if the process is still running
    if (process?.isAlive == false) return

    with(ous!!) {
      try {
        write("q\n".toByteArray())
        flush()
      } catch (e: Exception) {
        error("Error sending stop signal to ffmpeg process", throwable = e)
      }
    }
  }

  override suspend fun stop(exception: Exception?): Boolean {
    withIOContext {
      info("$downloadUrl stopping ffmpeg process...")
      ffprobeProcess?.destroy()
      sendStopSignal()
    }
    val code = withIOContext { process?.waitFor() }
    if (code != 0) {
      error("ffmpeg process exited with code $code")
    }
    return code == 0
  }
}