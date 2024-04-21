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
import github.hua0512.logger
import io.ktor.http.*

/**
 * @author hua0512
 * @date : 2024/3/20 21:29
 */

private fun buildDefaultFFMpegInputArgs(headers: Map<String, String> = emptyMap(), cookies: String? = null): Array<String> =
  mutableListOf<String>().apply {
    // ensure that the headers are properly separated
    if (headers.isNotEmpty()) {
      headers.forEach {
        val prefix = if (it.key == HttpHeaders.UserAgent) "-user_agent" else "-headers"
        add(prefix)
        add("${it.key}: ${it.value}")
      }
      // ensure that the headers are properly separated
      add("-headers")
      add("\r\n")
    }
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
  }.toTypedArray()

private fun buildDefaultFFMpegOutputArgs(downloadFormat: VideoFormat, segmentPart: Long, segmentTime: Long?): Array<String> {
  return mutableListOf<String>().apply {
    if (downloadFormat == VideoFormat.avi) {
      add("-bsf:v")
      add("h264_mp4toannexb")
    }
    // segment the file, according to the maxPartDuration
    if (segmentTime != null) {
      add("-to")
      add(segmentTime.toString())
    } else { // segment the file, according to the maxPartSize
      add("-fs")
      add(segmentPart.toString())
    }
  }.toTypedArray()
}


private fun buildFFMpegRunningCmd(
  defaultFFmpegInputArgs: Array<String>,
  defaultFFmpegOutputArgs: Array<String>,
  downloadUrl: String,
  downloadFormat: VideoFormat,
): Array<String> = defaultFFmpegInputArgs + arrayOf(
  "-i",
  downloadUrl,
) + defaultFFmpegOutputArgs + arrayOf(
  "-c",
  "copy",
  "-f",
  downloadFormat.ffmpegMuxer,
)


fun buildFFMpegCmd(
  headers: Map<String, String> = emptyMap(),
  cookies: String? = null,
  downloadUrl: String,
  downloadFormat: VideoFormat,
  segmentPart: Long,
  segmentTime: Long?,
  outputPath: String,
): Array<String> {

  // ffmpeg input args
  val defaultFFmpegInputArgs = buildDefaultFFMpegInputArgs(headers, cookies)

  // default output args
  val defaultFFmpegOutputArgs = buildDefaultFFMpegOutputArgs(downloadFormat, segmentPart, segmentTime)
  // build the ffmpeg command
  return buildFFMpegRunningCmd(defaultFFmpegInputArgs, defaultFFmpegOutputArgs, downloadUrl, downloadFormat) + outputPath
}


fun processFFmpegOutputLine(line: String, streamer: String, lastSize: Long, onDownloadProgress: (Long, Long, String) -> Unit) {
  if (!line.startsWith("size=")) {
    logger.info("$streamer - $line")
  } else if (line.contains("time=")) {
    //  size=     768kB time=00:00:02.70 bitrate=2330.2kbits/s speed=5.28x
    val sizeString = line.substringAfter("size=").substringBefore("time").trim()
    // extract the size in kB
    val size = sizeString.replace(Regex("[^0-9]"), "").toLong()
    val diff = size - lastSize
    val bitrate = line.substringAfter("bitrate=").substringBefore("speed").trim()
    onDownloadProgress(size, diff, bitrate)
  }
}