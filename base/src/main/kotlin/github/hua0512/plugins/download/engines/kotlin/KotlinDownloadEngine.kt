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

@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package github.hua0512.plugins.download.engines.kotlin

import github.hua0512.app.HttpClientFactory
import github.hua0512.download.DownloadLimitsProvider
import github.hua0512.download.DownloadPathProvider
import github.hua0512.download.DownloadProgressUpdater
import github.hua0512.plugins.download.engines.BaseDownloadEngine
import github.hua0512.utils.logger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Download engine using Ktor client
 * @author hua0512
 * @date : 2024/9/6 18:13
 */
abstract class KotlinDownloadEngine<T : Any> : BaseDownloadEngine() {

  protected val logger = logger(this::class.java)


  /**
   * Download job
   */
  lateinit var downloadJob: Job

  /**
   * FLV Producer channel
   */
  val producer: Channel<T> by lazy { Channel() }

  protected val client by lazy { HttpClientFactory().getClient(Json, installTimeout = false, installWebSockets = false) }


  // last downloaded segment time
  protected var lastDownloadedTime = 0L

  // last downloaded file path
  protected var lastDownloadFilePath: String = ""

  protected val limitsProvider: DownloadLimitsProvider = { fileLimitSize to (fileLimitDuration?.toFloat() ?: 0.0f) }

  abstract val pathProvider: DownloadPathProvider

  // last file size
  protected var lastSize = 0L

  protected val sizedUpdater: DownloadProgressUpdater = { size: Long, duration: Float, bitrate: Float ->
    val kbSizeDiff = (size - lastSize) / 1024
    if (kbSizeDiff > 0)
      onDownloadProgress(kbSizeDiff, bitrate.toInt().toDouble())
    lastSize = size
  }

  abstract fun ensureDownloadFormat(downloadUrl: String)

  override suspend fun start() = supervisorScope {
    ensureDownloadFormat(downloadUrl!!)
    try {
      downloadJob = launch {
        // check if its flv download url
        handleDownload()
      }

      processDownload()
      // await for download job to finish
      downloadJob.join()
      // case when download job is completed
      onDownloadFinished()
    } catch (e: Exception) {
      if (lastDownloadFilePath.isEmpty()) throw e
    } finally {
      if (downloadJob.isActive) downloadJob.cancel()
      client.close()
    }
  }

  abstract suspend fun handleDownload()

  abstract suspend fun processDownload()

  override suspend fun stop(exception: Exception?): Boolean {
    if (!this::downloadJob.isInitialized) return false
    downloadJob.cancelAndJoin()
    producer.close(exception)

    val result = withTimeoutOrNull(10000) {
      while (!producer.isClosedForReceive) {
        delay(500)
      }
    }

    if (result == null) {
      logger.warn("Producer channel not closed")
      return false
    }

    return true
  }
}