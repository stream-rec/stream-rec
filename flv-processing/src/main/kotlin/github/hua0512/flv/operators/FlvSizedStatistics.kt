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

package github.hua0512.flv.operators

import github.hua0512.download.DownloadProgressUpdater
import github.hua0512.flv.FlvParser
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.utils.isHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


/**
 * Extension function for Flow<FlvData> to calculate and update FLV file statistics.
 *
 * @param sizedUpdater Optional callback to receive updates on file size, duration, and bitrate.
 * @return A Flow<FlvData> that emits the original FlvData while calculating statistics.
 * @author hua0512
 * @date : 2024/9/11 23:54
 */
fun Flow<FlvData>.stats(sizedUpdater: DownloadProgressUpdater? = null): Flow<FlvData> = flow {


  var startAt = 0L
  var duration = 0f
  var fileSize = 0L

  /**
   * Resets the tracking variables to their initial state.
   */
  fun reset() {
    startAt = 0
    duration = 0f
    fileSize = 0
  }

  /**
   * Calculates the bitrate based on the file size and duration.
   *
   * @return The calculated bitrate in kbps.
   */
  fun calculateBitrate(): Float {
    val endAt = System.currentTimeMillis()
    val duration = (endAt - startAt) / 1000f
    val bitrate = fileSize * 8 / duration / 1024f // kbps
    return bitrate
  }

  /**
   * Updates the sizedUpdater callback with the current file size, duration, and bitrate.
   */
  fun update() {
    sizedUpdater?.apply {
      val bitrate = calculateBitrate()
      invoke(fileSize, duration, bitrate)
    }
  }

  // Collects the FlvData from the upstream flow
  collect { data ->
    when {
      data.isHeader() -> {
        reset()
        startAt = System.currentTimeMillis()
        fileSize += (data as FlvHeader).size + FlvParser.POINTER_SIZE
        update()
      }

      data is FlvTag -> {
        fileSize += data.size + FlvParser.POINTER_SIZE
        duration = data.header.timestamp / 1000f
        update()
      }

      else -> throw IllegalStateException("Unexpected FlvData type: $data")
    }
    emit(data)
  }

  reset()
  update()

}