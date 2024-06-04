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

import github.hua0512.data.media.VideoFormat
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val logger: Logger = LoggerFactory.getLogger("FFmpeg")


/**
 * Build the default ffmpeg input arguments
 * @author hua0512
 * @date : 2024/3/20 21:29
 */
private fun buildDefaultInputArgs(
  headers: Map<String, String> = emptyMap(),
  cookies: String? = null,
  enableLogger: Boolean = false,
  exitOnError: Boolean = false,
): Array<String> =
  mutableListOf<String>().apply {
    val headersString = StringBuilder()
    if (headers.isNotEmpty()) {
      add("-headers")
      headers.forEach {
        headersString.append("${it.key}: ${it.value}\n")
      }
      // add cookies if available
      if (cookies.isNullOrEmpty().not()) {
        headersString.append("Cookie: $cookies\n")
      }
      // add the headers
      add(headersString.toString().removeSuffix("\n"))

      // check if user agent is available
      if (headers[HttpHeaders.UserAgent] != null) {
        add("-user_agent")
        add(headers[HttpHeaders.UserAgent]!!)
      }

    } else {
      // add cookies if available
      if (cookies.isNullOrEmpty().not()) {
        add("-headers")
        add("Cookie: $cookies")
      }
    }
    add("-rw_timeout")
    add("20000000")
    if (enableLogger) {
      add("-loglevel")
      add("debug")
    }
    if (exitOnError) {
      // detect errors
      add("-xerror")
      // skip frames with no keyframes
      add("-skip_frame:v")
      add("nokey")
      // drop changed frames
      add("-flags:v")
      add("+drop_changed")
    }

  }.toTypedArray()

private fun buildDefaultOutputArgs(
  downloadFormat: VideoFormat,
  segmentPart: Long,
  segmentTime: Long?,
  useSegmentation: Boolean,
): Array<String> {
  return mutableListOf<String>().apply {
    if (downloadFormat == VideoFormat.avi) {
      add("-bsf:v")
      add("h264_mp4toannexb")
    }
    if (useSegmentation) {
      if (segmentPart != 0L) logger.debug("Ignoring segmentPart($segmentPart)s for segmentation, using segmentTime instead")
      // segment time, default is 2 hours
      val time = segmentTime ?: 2.toDuration(DurationUnit.HOURS).inWholeSeconds
      addAll(
        arrayOf(
          "-f",
          "segment",
          "-segment_time",
          time.toString(),
        )
      )
      if (downloadFormat == VideoFormat.mp4 || downloadFormat == VideoFormat.mov) {
        add("-segment_format_options")
        add("movflags=+faststart")
      }
      add("-reset_timestamps")
      add("1")
      add("-strftime")
      add("1")
    } else {
      // segment the file, according to the maxPartDuration
      if (segmentTime != null) {
        add("-to")
        add(segmentTime.toString())
      } else { // segment the file, according to the maxPartSize
        add("-fs")
        add(segmentPart.toString())
      }
    }
  }.toTypedArray()
}


private fun buildFFMpegRunningCmd(
  defaultFFmpegInputArgs: Array<String>,
  defaultFFmpegOutputArgs: Array<String>,
  downloadUrl: String,
  downloadFormat: VideoFormat,
  useSegmentation: Boolean = false,
  detectErrors: Boolean = false,
): Array<String> = defaultFFmpegInputArgs + mutableListOf<String>().apply {
  add("-i")
  add(downloadUrl)
  addAll(defaultFFmpegOutputArgs)
  if (detectErrors) {
    // add the video codec
    add("-c:v")
    add("copy")
    // add the audio codec
    add("-c:a")
    add("aac")
    // add the audio channels
    add("-ac")
    add("2")
  } else {
    // copy without re-encoding
    add("-c")
    add("copy")
  }

  if (!useSegmentation) {
    add("-f")
    add(downloadFormat.ffmpegMuxer)
  }
}


fun buildFFMpegCmd(
  headers: Map<String, String> = emptyMap(),
  cookies: String? = null,
  downloadUrl: String,
  downloadFormat: VideoFormat,
  segmentPart: Long,
  segmentTime: Long?,
  useSegmentation: Boolean? = false,
  exitOnError: Boolean = false,
  outputPath: String,
): Array<String> {
  // ffmpeg input args
  val defaultFFmpegInputArgs = buildDefaultInputArgs(headers, cookies, exitOnError = exitOnError)

  // default output args
  val defaultFFmpegOutputArgs = buildDefaultOutputArgs(downloadFormat, segmentPart, segmentTime, useSegmentation == true)
  // build the ffmpeg command
  return buildFFMpegRunningCmd(
    defaultFFmpegInputArgs,
    defaultFFmpegOutputArgs,
    downloadUrl,
    downloadFormat,
    useSegmentation == true,
    exitOnError
  ) + outputPath
}


internal fun processFFmpegOutputLine(
  line: String,
  streamer: String,
  lastSize: Long,
  onSegmentStarted: (String) -> Unit,
  onDownloadProgress: (Long, Long, String) -> Unit,
) {
  when {
    !line.startsWith("size=") -> {
      logger.info("$streamer - $line")
      // handle opening segment for writing
      if (line.startsWith("[segment @") && line.contains("Opening")) {
        // [segment @ 000001c2e7450a40] Opening '2024-05-05_22-37-27.mp4' for writing
        // extract the segment name
        val segmentName = line.substringAfter('\'').substringBefore('\'')
        onSegmentStarted(segmentName)
      }
    }

    line.contains("time=") -> {
      //  size=     768kB time=00:00:02.70 bitrate=2330.2kbits/s speed=5.28x
      val sizeString = line.substringAfter("size=").substringBefore("time").trim()
      // extract the size in kB
      val size = sizeString.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0
      val diff = size - lastSize
      val bitrate = line.substringAfter("bitrate=").substringBefore("speed").trim()
      onDownloadProgress(size, diff, bitrate)
    }
  }
}