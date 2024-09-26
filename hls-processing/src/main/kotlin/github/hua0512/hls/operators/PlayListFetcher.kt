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

import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.slogger
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.lindstrom.m3u8.model.MasterPlaylist
import io.lindstrom.m3u8.model.MediaPlaylist
import io.lindstrom.m3u8.parser.MasterPlaylistParser
import io.lindstrom.m3u8.parser.MediaPlaylistParser
import io.lindstrom.m3u8.parser.ParsingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory


/**
 * @author hua0512
 * @date : 2024/9/12 13:29

 */

class PlayListFetcher(val client: HttpClient, streamer: StreamerContext) {

  private var disposed = false
  private var debugEnabled = true

  private val logger = streamer.slogger(TAG)

  companion object {
    private const val TAG = "PlayListFetcher"
  }


  fun reset() {
    disposed = false
  }


  private suspend fun fetchPlaylist(url: String): String {
    try {
      val request = client.get(url) {
        timeout {
          // set 10 s timeout for the whole request
          requestTimeoutMillis = 10000
          connectTimeoutMillis = 10000
          socketTimeoutMillis = 10000
        }
        retry {
          this.maxRetries = 8
          this.exponentialDelay(1.0, maxDelayMs = 10000)
        }
      }
      val body = request.bodyAsText()
//      logger.debug("Fetched playlist: $body")
      return body
    } catch (e: Exception) {
      logger.debug("Failed to fetch playlist: {}", e.message)
      throw e
    }
  }

  private fun isMasterPlaylist(content: String) = content.contains("#EXT-X-STREAM-INF")

  private fun MasterPlaylist.getBestQualityVariant(): String {
    val variants = variants()
    if (variants.isEmpty()) {
      throw IllegalStateException("No variants found in master playlist")
    }
    val bestVariant = variants.maxByOrNull { it.bandwidth() }
    logger.debug("Best variant: {}", bestVariant)
    return bestVariant?.uri() ?: variants.first().uri()
  }


  suspend fun consume(urlFlow: String): Flow<MediaPlaylist> = flow {

    var url = urlFlow

    // loop until disposed
//    var disposed = this.currentCoroutineContext().isActive
    var delay = 3000L

    while (!disposed) {
      try {
        val playlistString = fetchPlaylist(url)
        // use lenient parsing mode
        val parsingMode = ParsingMode.LENIENT
        var isMaster = false
        // check if its master playlist
        val mediaParser = if (isMasterPlaylist(playlistString)) {
          isMaster = true
          MasterPlaylistParser(parsingMode)
        } else {
          MediaPlaylistParser(parsingMode)
        }
        val playlist = mediaParser.readPlaylist(playlistString)

        if (isMaster) {
          url = (playlist as MasterPlaylist).getBestQualityVariant()
          logger.debug("Using variant: $url")
          val mediaPlaylistFlow = consume(url)
          emitAll(mediaPlaylistFlow)
          return@flow
        }

        logger.debug("Parsed playlist: {}", playlist)
        playlist as MediaPlaylist
        delay = playlist.targetDuration() * 1000L
        emit(playlist)
      } catch (e: Exception) {
        logger.debug("Failed to fetch playlist: $e")
        throw e
      } finally {
        // use playlist target duration / 2 as delay
        delay(delay / 2)
      }
    }

  }


}