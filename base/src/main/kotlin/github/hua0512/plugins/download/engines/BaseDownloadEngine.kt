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
import github.hua0512.data.stream.FileInfo
import github.hua0512.data.stream.Streamer
import github.hua0512.logger
import github.hua0512.plugins.download.base.DownloadCallback
import github.hua0512.utils.rename
import java.util.*
import kotlin.io.path.*

/**
 * Base download engine
 * @author hua0512
 * @date : 2024/5/5 18:30
 */
abstract class BaseDownloadEngine {

  companion object {

    const val PART_PREFIX = "PART_"
  }


  protected var cookies: String? = ""
  protected var downloadUrl: String? = null
  protected var downloadFormat: VideoFormat? = null
  protected var downloadFilePath: String = ""
  protected var headers = mutableMapOf<String, String>()
  protected var downloads = mutableListOf<FileInfo>()
  protected var fileLimitDuration: Long? = null
  protected var fileLimitSize: Long = 0
  protected var isInitialized = false
  protected var streamer: Streamer? = null
  private var callback: DownloadCallback? = null


  /**
   * Instantiates the download engine with the required parameters.
   *
   * @param downloadUrl The URL of the video to be downloaded.
   * @param downloadFormat The format of the video to be downloaded.
   * @param downloadFilePath The file path where the video will be saved.
   * @param streamer The streamer object representing the source of the video.
   * @param cookies The optional cookies to be used for the download.
   * @param headers The optional headers to be used for the download.
   * @param fileLimitSize The optional file size limit for the download.
   * @param fileLimitDuration The optional duration limit for the download.
   * @param callback The callback object to receive download events.
   */
  fun init(
    downloadUrl: String,
    downloadFormat: VideoFormat,
    downloadFilePath: String,
    streamer: Streamer,
    cookies: String? = "",
    headers: Map<String, String> = emptyMap(),
    fileLimitSize: Long = 0,
    fileLimitDuration: Long? = null,
    callback: DownloadCallback,
  ) {
    this.downloadUrl = downloadUrl
    this.downloadFormat = downloadFormat
    ensureDownloadUrl()
    ensureDownloadFormat()
    this.downloadFilePath = downloadFilePath
    this.streamer = streamer
    this.cookies = cookies
    this.headers = headers.toMutableMap()
    this.fileLimitSize = fileLimitSize
    this.fileLimitDuration = fileLimitDuration
    this.callback = callback
    isInitialized = true
    onInit()
  }


  /**
   * Start the download process
   */
  abstract suspend fun start()

  /**
   * Stop the download process
   */
  abstract suspend fun stop(): Boolean

  /**
   * Clean up the resources
   */
  fun clean() {
    onDestroy()
  }

  /**
   * Ensures that the download URL is not null.
   * @throws IllegalArgumentException If the download URL is null.
   */
  private fun ensureDownloadUrl() {
    requireNotNull(downloadUrl) { "downloadUrl is null" }
    require(downloadUrl!!.isNotBlank()) { "downloadUrl is blank" }
  }

  private fun extractFormatFromPath(downloadFilePath: String): VideoFormat? {
    val extension = downloadFilePath.substringAfterLast(".").lowercase(Locale.getDefault())
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

  protected fun onInit() {
    callback?.onInit()
  }

  protected fun onDownloadStarted(filePath: String, time: Long) {
    callback?.onDownloadStarted(filePath, time)
  }

  protected fun onDownloadProgress(diff: Long, bitrate: String) {
    callback?.onDownloadProgress(diff, bitrate)
  }

  protected fun onDownloaded(data: FileInfo) {
    // calculate file size
    val oldPath = Path(data.path)
    // check if the file exists
    if (!oldPath.exists()) {
      logger.error("Downloaded file does not exist: {}", oldPath)
      return
    }
    logger.debug("Downloaded file: {}", oldPath)
    // remove the file name PART_ prefix
    val newPath = oldPath.parent.resolve(oldPath.name.removePrefix(PART_PREFIX))
    // rename the file
    oldPath.rename(newPath)
    // get the file size
    val fileSize = newPath.fileSize()
    // update the data
    val copy = data.copy(path = newPath.absolutePathString(), size = fileSize)
    callback?.onDownloaded(copy)
    downloads.add(copy)
  }

  protected fun onDownloadFinished() {
    callback?.onDownloadFinished()
  }

  protected fun onDownloadError(filePath: String?, e: Exception) {
    callback?.onDownloadError(filePath, e)
  }

  protected fun onDownloadCancelled() {
    callback?.onDownloadCancelled()
  }

  protected fun onDestroy() {
    callback?.onDestroy()
    downloads.clear()
  }
}