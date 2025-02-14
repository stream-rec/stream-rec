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

package github.hua0512.plugins.download.engines.kotlin

import github.hua0512.data.stream.FileInfo
import github.hua0512.download.exceptions.FatalDownloadErrorException
import github.hua0512.hls.data.HlsSegment
import github.hua0512.hls.operators.downloadHls
import github.hua0512.hls.operators.process
import github.hua0512.utils.debug
import github.hua0512.utils.replacePlaceholders
import io.ktor.http.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.Clock
import java.net.SocketTimeoutException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.pathString

/**
 * Kotlin download engine for HLS streams
 * @author hua0512
 * @date : 2024/10/11 18:45
 */
class KotlinHlsDownloadEngine : KotlinDownloadEngine<HlsSegment>() {


  /**
   * Whether to combine ts files
   */
  internal var combineTsFiles = false

  override val pathProvider: (Int) -> String = { index: Int ->
    val time = Clock.System.now()
    lastDownloadedTime = time.epochSeconds
    downloadFilePath.replacePlaceholders(context.name, context.title, context.platform, time).run {
      // use parent folder for m3u8 with combining files disabled
      lastDownloadFilePath = Path(this).let {
        it.createParentDirectories()
        if (!combineTsFiles) it.parent.pathString else it.pathString
      }
      lastDownloadFilePath
    }
  }

  override fun ensureDownloadFormat(downloadUrl: String) {
    val url = Url(downloadUrl)

    if (url.segments.lastOrNull()?.endsWith(".m3u8") == true) {
      return
    }
    throw FatalDownloadErrorException("Unsupported download format: $downloadUrl")
  }

  override suspend fun handleDownload() {
    downloadUrl!!
      .downloadHls(client, context)
      .onEach { producer.send(it) }
      .onCompletion { cause ->
        debug("Completed hls producer due to: $cause")
      }.collect()

    producer.close(SocketTimeoutException("HLS download completed"))
  }

  override suspend fun processDownload() {
    producer.receiveAsFlow()
      .process(
        context, limitsProvider,
        pathProvider,
        combineTsFiles,
        ::onDownloadStarted,
        sizedUpdater,
      ) { index, path, createdAt, openAt ->
        onDownloaded(FileInfo(path, Path.of(path).fileSize(), createdAt / 1000, openAt / 1000), null)
      }
      .onCompletion {
        debug("processHlsDownload completed : $it")
        if (it != null) {
          throw it // rethrow exception
        }
      }
      .collect()
  }


}