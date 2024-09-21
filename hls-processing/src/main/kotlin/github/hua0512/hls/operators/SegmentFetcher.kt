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

import github.hua0512.hls.data.HlsSegment
import github.hua0512.hls.data.HlsSegment.DataSegment
import github.hua0512.utils.logger
import github.hua0512.utils.mapConcurrently
import github.hua0512.utils.writeToOutputStream
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.lindstrom.m3u8.model.MediaSegment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.transform
import java.io.ByteArrayOutputStream
import java.util.Collections

private const val TAG = "SegmentFetcher"
private val logger = logger(TAG)

private const val MAX_CONCURRENCY = 3
private const val MAX_RETRIES = 3

private const val MAX_MAP_SIZE = 100

private val downloadedSegments = Collections.synchronizedMap(object : LinkedHashMap<String, Boolean>(MAX_MAP_SIZE, 0.75f, true) {
  override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>): Boolean {
    return size > MAX_MAP_SIZE
  }
})

private fun addSegment(segment: String) {
  downloadedSegments[segment] = true
}

@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<List<MediaSegment>>.download(downloadBaseUrl: String, client: HttpClient): Flow<HlsSegment> =
  transform { segments ->

    suspend fun downloadSegment(url: String): ByteArray? {
      val channel = try {
        client.get(url) {
          this.timeout {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
          }
          retry {
            maxRetries = MAX_RETRIES
            exponentialDelay(maxDelayMs = 10000)
          }
        }.bodyAsChannel()
      } catch (e: Exception) {
        logger.error("Failed to download segment: $url", e)
        return null
      }
      val bos = ByteArrayOutputStream()
      channel.writeToOutputStream(bos)
      val bytes = bos.toByteArray()
      return bytes
    }

    suspend fun downloadSegment(
      segment: MediaSegment,
      downloadBaseUrl: String,
    ): Pair<HlsSegment, MediaSegment>? {
      val url = downloadBaseUrl + segment.uri()
      val normalizedUri = segment.uri().substringBefore("?")
      return if (downloadedSegments.contains(normalizedUri)) {
        logger.debug("Segment already downloaded: $normalizedUri")
        null
      } else {
        logger.debug("Downloading segment: $url")
        downloadSegment(url)?.let {
          logger.debug("Downloaded segment: ${normalizedUri}, duration = ${segment.duration()}")
          addSegment(normalizedUri)
          DataSegment(normalizedUri, segment.duration(), it) to segment
        } ?: run {
          logger.error("Failed to download segment: $normalizedUri")
          null
        }
      }
    }


    // download segments concurrently preserving order
    segments.asFlow()
      .mapConcurrently(MAX_CONCURRENCY, MAX_CONCURRENCY + 1) { segment ->
        downloadSegment(segment, downloadBaseUrl)
      }
      .filterNotNull()
      .collect {
        logger.debug("Segment d: {}", it.second)
        emit(it.first)
      }
  }

