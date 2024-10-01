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

package github.hua0512.hls.operators

import github.hua0512.download.*
import github.hua0512.hls.data.HlsSegment
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn


private const val TAG = "HlsProcessor"
private val logger by lazy { logger(TAG) }

/**
 * @author hua0512
 * @date : 2024/9/24 10:15
 */
fun String.downloadHls(
  client: HttpClient,
  context: StreamerContext,
): Flow<HlsSegment> {
  val fetcher = PlayListFetcher(client, context)
  val separator = "/"
  val baseUrl = this.substringBeforeLast(separator) + separator
  return fetcher.consume(this)
    .resolve(context)
    .download(context, baseUrl, client)
    .catch {
      emit(HlsSegment.EndSegment)
      throw it
    }
    .flowOn(Dispatchers.IO)
}


fun Flow<HlsSegment>.process(
  context: StreamerContext,
  limitsProvider: DownloadLimitsProvider,
  pathProvider: DownloadPathProvider,
  combineTsFiles: Boolean,
  onDownloadStarted: OnDownloadStarted,
  onDownloadProgressUpdater: DownloadProgressUpdater,
  onDownloaded: OnDownloaded = { _, _, _, _ -> },
): Flow<HlsSegment> {
  val isOneFile = combineTsFiles
  return this.limit(context, limitsProvider)
    .dump(context, pathProvider, isOneFile, onDownloadStarted, onDownloaded)
    .dumpPlaylist(context, !isOneFile, pathProvider, onDownloadStarted, onDownloaded)
    .stats(onDownloadProgressUpdater)
    .flowOn(Dispatchers.IO)
}