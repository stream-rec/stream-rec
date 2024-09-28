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

package github.hua0512.download

/**
 * Type alias for a function that updates the download progress.
 *
 * @param fileSize The size of the file being downloaded.
 * @param duration The duration of the download.
 * @param bitrate The bitrate of the download.
 */
typealias DownloadProgressUpdater = (fileSize: Long, duration: Float, bitrate: Float) -> Unit

/**
 * Type alias for a function that provides download limits.
 *
 * @return A pair containing the maximum download size and the maximum duration of the download.
 */
typealias DownloadLimitsProvider = () -> Pair<Long, Float>

/**
 * Type alias for a function that provides the download path.
 *
 * @param index The index of the download.
 * @return The path to save the downloaded file.
 */
typealias DownloadPathProvider = (index: Int) -> String

typealias OnDownloaded = (index: Int, path: String, createAt: Long, dumpedAt: Long) -> Unit

typealias OnDownloadStarted = (path: String, createAt: Long) -> Unit