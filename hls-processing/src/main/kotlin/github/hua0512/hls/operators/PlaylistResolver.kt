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

import io.lindstrom.m3u8.model.MediaPlaylist
import io.lindstrom.m3u8.model.MediaSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * @author hua0512
 * @date : 2024/9/12 21:31
 */

private const val TAG = "PlayListResolver"
private val logger = LoggerFactory.getLogger(TAG)

private val NUMBER_REGEX = """(\d+)""".toRegex()


fun Flow<MediaPlaylist>.resolve(): Flow<List<MediaSegment>> = flow {

  var lastMediaSequence = 0L
  var lastSequenceNumber = 0L

  var attempts = 0

  fun reset() {
    lastMediaSequence = 0
    lastSequenceNumber = 0
    attempts = 0
  }

  var discontinuity = false

  collect {
    val mediaSequence = it.mediaSequence()
    if (mediaSequence < lastMediaSequence) {
      logger.warn("Playlist discontinuity detected: $lastMediaSequence -> $mediaSequence")
      discontinuity = true
      lastSequenceNumber = 0
    }

    if (mediaSequence - 1 != lastMediaSequence) {
      logger.warn("Playlist sequence discontinuity detected: $lastSequenceNumber -> $mediaSequence")
      discontinuity = true
    }

    lastMediaSequence = mediaSequence

    val segments = it.mediaSegments()


    // check if segments discontinuity exists
    if (segments.any { it.discontinuity() }) {
      logger.warn("Segment discontinuity detected in playlist")
    }



    if (segments.isEmpty()) {
      attempts++
      if (attempts > 3) {
        logger.error("No segments found in playlist")
        attempts = 0
        throw IllegalStateException("No segments found in playlist")
      }
      return@collect
    } else {
      attempts = 0
    }
//    logger.debug("Emitting segment: {}", segments)
    emit(segments)
  }

  reset()

}