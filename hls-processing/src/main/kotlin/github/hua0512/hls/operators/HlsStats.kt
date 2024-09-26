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

import github.hua0512.download.DownloadProgressUpdater
import github.hua0512.hls.data.HlsSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


/**
 * @author hua0512
 * @date : 2024/9/26 14:01
 */

internal fun Flow<HlsSegment>.stats(progressUpdater: DownloadProgressUpdater) = flow<HlsSegment> {

  var size = 0L
  var duration = 0f
  var startTime = 0L


  fun reset() {
    size = 0
    duration = 0f
    startTime = 0
  }

  fun calculateBitrate(): Float {
    val endAt = System.currentTimeMillis()
    val duration = (endAt - startTime) / 1000f
    val bitrate = size * 8 / duration / 1024f // kbps
    return bitrate
  }

  collect {
    if (it is HlsSegment.EndSegment) {
      reset()
      progressUpdater(0, 0f, 0f)
      emit(it)
      return@collect
    }

    if (size == 0L) {
      startTime = System.currentTimeMillis()
    }

    it as HlsSegment.DataSegment
    size += it.data.size
    duration += it.duration.toFloat()
    val bitrate = calculateBitrate()
    progressUpdater(size, duration, bitrate)
    emit(it)
  }

  reset()
}