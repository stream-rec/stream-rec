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

import github.hua0512.data.stream.FileInfo
import github.hua0512.flv.FlvAnalyzerSizedUpdater
import github.hua0512.flv.FlvMetaInfoProvider
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.operators.PathProvider
import github.hua0512.flv.operators.analyze
import github.hua0512.flv.operators.dump
import github.hua0512.flv.operators.process
import github.hua0512.logger
import github.hua0512.utils.replacePlaceholders
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.nio.file.Path
import kotlin.io.path.fileSize

/**
 * Download engine using Ktor client
 * @author hua0512
 * @date : 2024/9/6 18:13
 */
class KotlinDownloadEngine : BaseDownloadEngine() {

  /**
   * Download job
   */
  private lateinit var downloadJob: Job

  /**
   * Producer channel
   */
  private lateinit var producer: Channel<FlvData>

  /**
   * Meta info provider
   */
  private val metaInfoProvider = FlvMetaInfoProvider()

  override suspend fun start() = coroutineScope {
    val client = HttpClient(OkHttp) {
      engine {
        config {
          followRedirects(true)
        }
        request {
          timeout {
            requestTimeoutMillis = 30000
          }
        }
      }
    }
    producer = Channel<FlvData>()

    val pathProvider: PathProvider = { index ->
      val time = Clock.System.now()
      downloadFilePath.replacePlaceholders(streamer!!.name, index.toString(), time).also {
        onDownloadStarted(it, time.epochSeconds)
      }
    }

    val limitsProvider = { fileLimitSize to (fileLimitDuration?.toFloat() ?: 0.0f) }

    downloadJob = launch {
      client.prepareGet(downloadUrl!!) {
        this@KotlinDownloadEngine.headers.forEach {
          header(it.key, it.value)
        }
        this@KotlinDownloadEngine.cookies?.let { header(HttpHeaders.Cookie, it) }
      }.execute { httpResponse ->
        val channel = httpResponse.bodyAsChannel()
        channel.asStreamFlow().collect { producer.send(it) }
      }

      this.coroutineContext[Job]?.invokeOnCompletion {
        producer.close()
      }
    }

    // last file size
    var lastSize = 0L

    val sizedUpdater: FlvAnalyzerSizedUpdater = { size: Long, duration: Float, bitrate: Float ->
      val kbSizeDiff = (size - lastSize) / 1024
      if (kbSizeDiff > 0)
        onDownloadProgress(kbSizeDiff, bitrate.toInt().toDouble())
      lastSize = size
    }

    producer.receiveAsFlow()
      .process(limitsProvider)
      .analyze(metaInfoProvider, sizedUpdater)
      .dump(pathProvider) { index, path, createdAt, openAt ->
        val metaInfo = metaInfoProvider[index] ?: run {
          logger.warn("$index meta info not found")
          return@dump
        }
        onDownloaded(FileInfo(path, Path.of(path).fileSize(), createdAt / 1000, openAt / 1000), metaInfo)
        metaInfoProvider.remove(index)
      }
      .flowOn(Dispatchers.IO)
      .onCompletion {
        // clear meta info provider when completed
        metaInfoProvider.clear()
      }
      .collect()

    // await for download job to finish
    downloadJob.join()
    // download finished
    onDownloadFinished()
    client.close()
  }

  override suspend fun stop(): Boolean {
    if (this::downloadJob.isInitialized) {
      downloadJob.cancel()
      if (this::producer.isInitialized) {
        producer.close()
      }
      return true
    }
    return false
  }
}