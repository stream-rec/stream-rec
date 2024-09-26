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

import github.hua0512.download.DownloadLimitsProvider
import github.hua0512.hls.data.HlsSegment
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import github.hua0512.utils.slogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


private const val TAG = "HlsLimit"

/**
 * Limit the flow of [HlsSegment] based on the [limitProvider]
 * @param limitProvider the provider of the limits
 * @return a flow of [HlsSegment] with limits applied
 * @author hua0512
 * @date : 2024/9/21 14:09
 */
internal fun Flow<HlsSegment>.limit(context: StreamerContext, limitProvider: DownloadLimitsProvider): Flow<HlsSegment> = flow {

  val logger = context.slogger(TAG)


  var duration = 0.0
  var size = 0L

  var (maxSize, maxDuration) = limitProvider()


  fun reset() {
    duration = 0.0
    size = 0
  }

  fun isLimitReached() = (maxSize != 0L && size >= maxSize) || (maxDuration != 0.0f && duration >= maxDuration)

  collect { value ->
    // if limit reached, end the segment
    if (isLimitReached()) {
      logger.info("Limit reached, end hls...")
      emit(HlsSegment.EndSegment)
      reset()
    }
    // if segment is data segment, add to duration and size
    if (value is HlsSegment.DataSegment) {
      duration += value.duration
      size += value.data.size
//        logger.trace("Duration: $duration, Size: $size")
    }

    emit(value)
  }

  reset()
  logger.debug("$TAG end")
}