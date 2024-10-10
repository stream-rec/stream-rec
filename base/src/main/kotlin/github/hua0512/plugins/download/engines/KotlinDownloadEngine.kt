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

@file:OptIn(ExperimentalCoroutinesApi::class)

package github.hua0512.plugins.download.engines

import github.hua0512.app.HttpClientFactory
import github.hua0512.data.stream.FileInfo
import github.hua0512.download.DownloadLimitsProvider
import github.hua0512.download.DownloadPathProvider
import github.hua0512.download.DownloadProgressUpdater
import github.hua0512.download.OnDownloadStarted
import github.hua0512.download.exceptions.FatalDownloadErrorException
import github.hua0512.flv.FlvMetaInfoProvider
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.exceptions.FlvHeaderErrorException
import github.hua0512.flv.operators.analyze
import github.hua0512.flv.operators.dump
import github.hua0512.flv.operators.process
import github.hua0512.flv.operators.stats
import github.hua0512.flv.utils.asStreamFlow
import github.hua0512.hls.data.HlsSegment
import github.hua0512.hls.operators.downloadHls
import github.hua0512.hls.operators.process
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.mainLogger
import github.hua0512.utils.replacePlaceholders
import github.hua0512.utils.writeToFile
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

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
   * FLV Producer channel
   */
  private lateinit var producer: Channel<FlvData>

  /**
   * HLS Producer channel
   */
  private lateinit var hlsProducer: Channel<HlsSegment>

  /**
   * Meta info provider
   */
  private val metaInfoProvider by lazy { FlvMetaInfoProvider() }

  /**
   * Whether to enable flv fix
   */
  internal var enableFlvFix = false

  internal var enableFlvDuplicateTagFiltering = true

  /**
   * Whether to combine ts files
   */
  internal var combineTsFiles = false

  // last downloaded segment time
  private var lastDownloadedTime = 0L

  // last downloaded file path
  private var lastDownloadFilePath: String = ""

  override suspend fun start() = coroutineScope {
    val client = HttpClientFactory().getClient(
      Json,
      installTimeout = false,
      installWebSockets = false
    )

    val isFlv = when {
      downloadUrl!!.contains(".flv") -> {
        producer = Channel<FlvData>()
        true
      }

      downloadUrl!!.contains(".m3u8") -> {
        hlsProducer = Channel<HlsSegment>()
        false
      }

      else -> throw FatalDownloadErrorException("Unsupported download URL: $downloadUrl")
    }

    val downloadStartCallback: OnDownloadStarted = ::onDownloadStarted

    val pathProvider = { index: Int ->
      val time = Clock.System.now()
      lastDownloadedTime = time.epochSeconds
      downloadFilePath.replacePlaceholders(streamer!!.name, index.toString(), time).also {
        if (isFlv)
          downloadStartCallback(it, time.epochSeconds)
      }.run {
        // use parent folder for m3u8 with combining files disabled
        lastDownloadFilePath = if (!isFlv && !combineTsFiles) {
          Path(this).parent.pathString
        } else if (isFlv) {
          // force flv file extension
          val path = Path(this)
          path.resolveSibling("${path.nameWithoutExtension}.flv").pathString
        } else
          this
        lastDownloadFilePath
      }
    }

    val limitsProvider = { fileLimitSize to (fileLimitDuration?.toFloat() ?: 0.0f) }
    // last file size
    var lastSize = 0L

    val sizedUpdater: DownloadProgressUpdater = { size: Long, duration: Float, bitrate: Float ->
      val kbSizeDiff = (size - lastSize) / 1024
      if (kbSizeDiff > 0)
        onDownloadProgress(kbSizeDiff, bitrate.toInt().toDouble())
      lastSize = size
    }

    val streamerContext = StreamerContext(streamer!!.name, "")

    downloadJob = launch {
      // check if its flv download url
      if (isFlv) {
        handleFlvDownload(client, pathProvider, sizedUpdater, streamerContext)
      } else {
        handleHlsDownload(client, streamerContext)
      }
    }

    try {
      if (isFlv && enableFlvFix) {
        processFlvDownload(streamerContext, pathProvider, limitsProvider, sizedUpdater)
      } else if (!isFlv) {
        processHlsDownload(streamerContext, limitsProvider, pathProvider, downloadStartCallback, sizedUpdater)
      }
      // await for download job to finish
      downloadJob.join()
      // case when download job is completed
      onDownloadFinished()
    } finally {
      if (downloadJob.isActive) downloadJob.cancel()
      client.close()
    }
  }


  private suspend fun handleFlvDownload(
    client: HttpClient,
    pathProvider: DownloadPathProvider,
    sizedUpdater: DownloadProgressUpdater,
    streamerContext: StreamerContext,
  ) {
    var exception: Throwable? = null
    client.prepareGet(downloadUrl!!) {
      this@KotlinDownloadEngine.headers.forEach { header(it.key, it.value) }
      cookies?.let { header(HttpHeaders.Cookie, it) }
    }.execute { httpResponse ->
      val channel = httpResponse.bodyAsChannel()
      if (enableFlvFix) {
        channel
          .asStreamFlow(context = streamerContext)
          .catch {
            exception = it
          }
          .onEach { producer.send(it) }
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
    mainLogger.debug("${streamerContext.name} flv download completed, exception: $exception")
    if (exception is FlvHeaderErrorException) {
      if (producer.isEmpty) {
        producer.close(exception)
        onDownloadError(lastDownloadFilePath, exception as Exception)
        throw exception!!
      }
    } else if (exception is CancellationException || exception is kotlinx.coroutines.CancellationException) {
      exception = null
    }
    producer.close(exception)
  }

  private suspend fun handleHlsDownload(client: HttpClient, streamerContext: StreamerContext) {
    downloadUrl!!
      .downloadHls(client, streamerContext)
      .onEach { hlsProducer.send(it) }
      .onCompletion { cause ->
        mainLogger.debug("${streamerContext.name} Completed hls producer due to: $cause")
      }.collect()

    hlsProducer.close(SocketTimeoutException("HLS download completed"))
  }


  private suspend fun processFlvDownload(
    context: StreamerContext,
    pathProvider: DownloadPathProvider,
    limitsProvider: DownloadLimitsProvider,
    sizedUpdater: DownloadProgressUpdater,
  ) {
    producer.receiveAsFlow()
      .process(limitsProvider, context, enableFlvDuplicateTagFiltering)
      .analyze(metaInfoProvider, context)
      .dump(pathProvider) { index, path, createdAt, openAt ->
        val metaInfo = metaInfoProvider[index] ?: run {
          mainLogger.warn("${context.name} $index meta info not found")
          return@dump
        }
        onDownloaded(FileInfo(path, Path.of(path).fileSize(), createdAt / 1000, openAt / 1000), metaInfo)
        metaInfoProvider.remove(index)
      }
      .flowOn(Dispatchers.IO)
      .stats(sizedUpdater)
      .flowOn(Dispatchers.Default)
      .onCompletion { cause ->
        mainLogger.debug("${context.name} flv process completed : {}, {}", cause, metaInfoProvider.size)
        // nothing is downloaded
        if (metaInfoProvider.size == 0 && cause != null) {
          // clear meta info provider when completed
          metaInfoProvider.clear()
          onDownloadError(lastDownloadFilePath, cause as Exception)
          throw cause
          return@onCompletion
        }
        // case when download is completed with more than 0 segments is downloaded
        metaInfoProvider.clear()
      }
      .collect()
  }

  private suspend fun processHlsDownload(
    streamerContext: StreamerContext,
    limitsProvider: DownloadLimitsProvider,
    pathProvider: DownloadPathProvider,
    downloadStartCallback: OnDownloadStarted,
    sizedUpdater: DownloadProgressUpdater,
  ) {
    hlsProducer.receiveAsFlow()
      .process(
        streamerContext, limitsProvider,
        pathProvider,
        combineTsFiles,
        downloadStartCallback,
        sizedUpdater,
      ) { index, path, createdAt, openAt ->
        onDownloaded(FileInfo(path, Path.of(path).fileSize(), createdAt / 1000, openAt / 1000), null)
      }
      .onCompletion {
        mainLogger.debug("processHlsDownload completed : $it")
        if (it != null) {
          throw it // rethrow exception
        }
      }
      .collect()
  }

  @OptIn(DelicateCoroutinesApi::class)
  override suspend fun stop(exception: Exception?): Boolean {
    if (!this::downloadJob.isInitialized) return false
    downloadJob.cancelAndJoin()
    listOfNotNull(
      if (this::producer.isInitialized) producer else null,
      if (this::hlsProducer.isInitialized) hlsProducer else null
    ).forEach { channel ->
      channel.close()
      val result = withTimeoutOrNull(10000) {
        while (!channel.isClosedForReceive) {
          delay(1000)
        }
      }
      if (result == null) {
        mainLogger.warn("${channel::class.simpleName} channel not closed")
        return false
      }
    }
    return true
  }
}