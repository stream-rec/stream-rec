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

package github.hua0512.plugins.download.base

import github.hua0512.data.stream.FileInfo
import github.hua0512.flv.data.other.FlvMetadataInfo

/**
 * Interface defining callback methods for download events.
 * Implement this interface to handle various stages of the download process.
 *
 * Methods:
 * - `onInit()`: Called when the download process is initialized.
 * - `onDownloadStarted(filePath: String, time: Long)`: Called when the download starts.
 * - `onDownloadProgress(diff: Long, bitrate: Double)`: Called to report download progress.
 * - `onDownloaded(data: FileInfo, metaInfo: FlvMetadataInfo? = null)`: Called when a file is downloaded.
 * - `onDownloadFinished()`: Called when the download is finished.
 * - `onDownloadError(filePath: String?, e: Exception)`: Called when an error occurs during download.
 * - `onDownloadCancelled()`: Called when the download is cancelled.
 * - `onDestroy()`: Called when the download process is destroyed.
 *
 * @see FileInfo
 * @see FlvMetadataInfo
 *
 * Author: hua0512
 * Date: 2024/5/5 18:31
 */
interface DownloadCallback {

  /**
   * Called when the download process is initialized.
   */
  fun onInit()

  /**
   * Called when the download starts.
   *
   * @param filePath The path of the file being downloaded.
   * @param time The start time of the download, in epoch seconds.
   */
  fun onDownloadStarted(filePath: String, time: Long)

  /**
   * Called to report download progress.
   *
   * @param diff The difference in bytes downloaded since the last progress update.
   * @param bitrate The current bitrate of the download.
   */
  fun onDownloadProgress(diff: Long, bitrate: Double)

  /**
   * Called when a file is downloaded.
   *
   * @param data The downloaded file information.
   * @param metaInfo Optional metadata information associated with the file.
   */
  fun onDownloaded(data: FileInfo, metaInfo: FlvMetadataInfo? = null)

  /**
   * Called when the download is finished.
   */
  fun onDownloadFinished()

  /**
   * Called when an error occurs during download.
   *
   * @param filePath The path of the file that caused the error, if available.
   * @param e The exception that occurred.
   */
  fun onDownloadError(filePath: String?, e: Exception)

  /**
   * Called when the download is cancelled.
   */
  fun onDownloadCancelled()

  /**
   * Called when the download process is destroyed.
   */
  fun onDestroy()

}