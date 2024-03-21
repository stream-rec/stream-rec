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
import github.hua0512.data.stream.StreamData
import github.hua0512.logger
import github.hua0512.utils.rename
import github.hua0512.utils.withIOContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.pathString

/**
 * Base download engine
 * @author hua0512
 * @date : 2024/2/12 18:22
 */
abstract class BaseDownloadEngine() {

  protected var onDownloadStarted: () -> Unit = {}
  protected var onDownloadProgress: (diff: Long, bitrate: String) -> Unit = { _, _ -> }
  protected var onDownloadFinished: (StreamData) -> Unit = {}

  protected var cookies: String? = ""
  protected var downloadUrl: String? = null
  protected var downloadFormat: VideoFormat? = null
  protected var downloadFilePath: String = ""
  protected var headers = mutableMapOf<String, String>()
  protected var streamData: StreamData? = null
  protected var startTime: Instant = Instant.DISTANT_PAST
  protected var fileLimitDuration: Long? = null
  protected var fileLimitSize: Long = 0
  protected var isInitialized = false

  /**
   * Initializes the download engine with the provided parameters.
   *
   * @param downloadUrl The URL of the file to be downloaded.
   * @param downloadFilePath The path where the downloaded file will be saved.
   * @param streamData The stream data, including the ID, title, start and end dates, output file path, and streamer information.
   * @param cookies The optional cookies to be used for the download. Defaults to an empty string.
   * @param headers The headers to be included in the download request.
   * @param startTime The start time of the download. Defaults to the current system time.
   * @param fileLimitSize The maximum allowed file size. Defaults to 0, which means no limit.
   */
  fun init(
    downloadUrl: String,
    downloadFormat: VideoFormat,
    downloadFilePath: String,
    streamData: StreamData,
    cookies: String? = "",
    headers: Map<String, String>,
    startTime: Instant = Clock.System.now(),
    fileLimitSize: Long = 0,
    fileLimitDuration: Long? = null,
  ) {
    if (downloadUrl.isBlank() || downloadFilePath.isBlank()) {
      throw IllegalArgumentException("downloadUrl or downloadFilePath is blank")
    }
    this.downloadUrl = downloadUrl
    this.downloadFormat = downloadFormat
    this.downloadFilePath = downloadFilePath
    this.streamData = streamData
    this.cookies = cookies
    this.headers = headers.toMutableMap()
    this.startTime = startTime
    this.fileLimitSize = fileLimitSize
    this.fileLimitDuration = fileLimitDuration
    isInitialized = true
  }

  /**
   * Starts the download process.
   *
   * @return The stream data of the downloaded file.
   */
  open suspend fun run(): StreamData? {
    if (!isInitialized) {
      throw IllegalStateException("Engine is not initialized")
    }
    return withIOContext {
      ensureDownloadUrl()
      ensureDownloadFormat()
      logger.info("Starting download: $downloadUrl, format: $downloadFormat, path: $downloadFilePath")
      startDownload()?.let { data ->
        val filePath = Path(data.outputFilePath).run {
          // remove .part suffix
          val withoutPart = pathString.removeSuffix(".part")
          rename(Path(withoutPart))
          Path(withoutPart)
        }
        // get file size
        val fileSize = filePath.fileSize()
        data.outputFileSize = fileSize
        data.copy(outputFilePath = filePath.pathString).also {
          onDownloadFinished(it)
        }
      }
    }
  }

  /**
   * Starts the download process.
   *
   * @return The stream data of the downloaded file, or null if the download failed.
   */
  abstract suspend fun startDownload(): StreamData?

  /**
   * Sets the callback to be executed when the download starts.
   *
   * @param callback The callback to be executed when the download starts.
   */
  fun onDownloadStarted(callback: () -> Unit) {
    onDownloadStarted = callback
  }

  /**
   * Sets the callback to be executed when the download progresses.
   *
   * @param callback The callback to be executed when the download progresses.
   */
  fun onDownloadProgress(callback: (diff: Long, bitrate: String) -> Unit) {
    onDownloadProgress = callback
  }

  /**
   * Sets the callback to be executed when the download finishes.
   *
   * @param callback The callback to be executed when the download finishes.
   */
  fun onDownloadFinished(callback: (StreamData) -> Unit) {
    onDownloadFinished = callback
  }

  private fun extractFormatFromPath(downloadFilePath: String): VideoFormat? {
    val extension = downloadFilePath.removeSuffix(".part").substringAfterLast(".").lowercase(Locale.getDefault())
    return VideoFormat.format(extension)
  }

  /**
   * Ensures that the download format is not null.
   * If it is null, it tries to extract it from the download file path.
   * If it is still null, it throws an exception.
   * @throws IllegalArgumentException If the download format is null.
   */
  private fun ensureDownloadFormat() {
    downloadFormat = downloadFormat ?: extractFormatFromPath(downloadFilePath) ?: throw IllegalArgumentException("downloadFormat is null")
  }

  /**
   * Ensures that the download URL is not null.
   * @throws IllegalArgumentException If the download URL is null.
   */
  private fun ensureDownloadUrl() {
    downloadUrl?.let {
      if (it.isBlank()) {
        throw IllegalArgumentException("downloadUrl is blank")
      }
    } ?: throw IllegalArgumentException("downloadUrl is null")
  }
}