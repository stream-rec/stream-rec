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
import github.hua0512.download.exceptions.DownloadErrorException
import github.hua0512.download.exceptions.FatalDownloadErrorException
import github.hua0512.flv.FlvMetaInfoProvider
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.operators.analyze
import github.hua0512.flv.operators.dump
import github.hua0512.flv.operators.process
import github.hua0512.flv.operators.stats
import github.hua0512.flv.utils.asStreamFlow
import github.hua0512.utils.debug
import github.hua0512.utils.replacePlaceholders
import github.hua0512.utils.warn
import github.hua0512.utils.writeToFile
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.*

/**
 * Kotlin download engine for flv format
 * @author hua0512
 * @date : 2024/10/11 18:40
 */
class KotlinFlvDownloadEngine : KotlinDownloadEngine<FlvData>() {

  companion object {
    /**
     * Default socket timeout
     * 20 seconds
     */
    private const val DEFAULT_SOCKET_TIMEOUT = 20000L
  }


  override val pathProvider: (Int) -> String = { _ ->
    val time = Clock.System.now()
    lastDownloadedTime = time.epochSeconds
    downloadFilePath.replacePlaceholders(context.name, context.title, context.platform, time).let {
      val path = Path(it).apply {
        createParentDirectories()
        onDownloadStarted(it, time.epochSeconds)
      }
      lastDownloadFilePath = path.resolveSibling("${path.nameWithoutExtension}.flv").pathString
      lastDownloadFilePath
    }
  }


  override fun ensureDownloadFormat(downloadUrl: String) {
    val url = Url(downloadUrl)

    if (url.segments.lastOrNull()?.endsWith(".flv") == true)
      return

    throw FatalDownloadErrorException("Unsupported download format: $downloadUrl")
  }

  /**
   * Whether to enable flv fix
   */
  internal var enableFlvFix = false

  internal var enableFlvDuplicateTagFiltering = true

  /**
   * Meta info provider
   */
  private val metaInfoProvider by lazy { FlvMetaInfoProvider() }

  override suspend fun handleDownload() {
    var exception: Throwable? = null
    try {
      client.prepareGet(downloadUrl!!) {
        this@KotlinFlvDownloadEngine.headers.forEach { header(it.key, it.value) }
        cookies?.let { header(HttpHeaders.Cookie, it) }

        timeout {
          // 20 seconds
          socketTimeoutMillis = DEFAULT_SOCKET_TIMEOUT
        }

      }.execute { httpResponse ->
        if (!httpResponse.status.isSuccess()) {
          exception = DownloadErrorException("Failed to download flv, status: ${httpResponse.status}")
          return@execute
        }
        val channel = httpResponse.bodyAsChannel()
        if (enableFlvFix) {
          channel
            .asStreamFlow(context = context)
            .catch {
              exception = it
            }
            .onEach {
              producer.send(it)
            }
            .flowOn(Dispatchers.IO)
            .collect()
        } else {
          val outputPath = pathProvider(0)
          val file = Path.of(outputPath).toFile()
          channel.writeToFile(file, sizedUpdater) {
            onDownloaded(FileInfo(outputPath, file.length(), lastDownloadedTime, file.lastModified() / 1000), null)
          }
        }
      }
    } catch (e: Exception) {
      exception = e
    }
    debug("flv download completed, exception: $exception")
    if (exception is CancellationException) {
      exception = null
    }
    producer.close(exception)
  }

  override suspend fun processDownload() {
    var lastStreamIndex = -1
    producer.receiveAsFlow()
      .process(limitsProvider, context, enableFlvDuplicateTagFiltering)
      .analyze(metaInfoProvider, context)
      .dump(pathProvider) { index, path, createdAt, openAt ->
        val metaInfo = metaInfoProvider[index] ?: run {
          warn("$index meta info not found")
          return@dump
        }
        onDownloaded(FileInfo(path, Path.of(path).fileSize(), createdAt / 1000, openAt / 1000), metaInfo)
        lastStreamIndex = index
        metaInfoProvider.remove(index)
      }
      .flowOn(Dispatchers.IO)
      .stats(sizedUpdater)
      .flowOn(Dispatchers.Default)
      .onCompletion { cause ->
        debug("flv process completed : {}, {}", cause, lastStreamIndex)
        // nothing is downloaded
        if (lastStreamIndex == -1) {
          // clear meta info provider when completed
          metaInfoProvider.clear()
          // when exception is null, download is completed without any segments(user cancelled, triggered by cancellation)
          // non-null exceptions are due to IO, parsing, etc. errors exceptions
          val realCause = cause ?: FatalDownloadErrorException("No segments downloaded")
          // remove PART prefix as onDownloaded is called before and the file is renamed
          onDownloadError(lastDownloadFilePath.replace(PART_PREFIX, ""), realCause as Exception)
          return@onCompletion
        }
        // case when download is completed, and 1 or more segments are downloaded
        // in those cases, we ignore the exception and just await for the final callback.
        metaInfoProvider.clear()
        lastStreamIndex = -1
        cause?.let {
          onDownloadError(lastDownloadFilePath.replace(PART_PREFIX, ""), it as Exception)
          throw it
        }
      }
      .collect()
  }


}