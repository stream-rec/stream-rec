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

package github.hua0512.flv.data.tag

import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import github.hua0512.flv.utils.writeI24
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.serialization.Serializable
import kotlin.experimental.and

/**
 * FLV tag header representation
 * 11 bytes tag header
 * @property tagType FLV tag type
 * @property dataSize FLV tag data size
 * @property timestamp FLV tag timestamp
 * @property streamId FLV tag stream id
 * @author hua0512
 * @date : 2024/6/8 18:39
 */
@Serializable
data class FlvTagHeader(
  /**
   * FLV tag type, 1 byte
   */
  val tagType: FlvTagHeaderType,
  /**
   * FLV tag data size, 3 bytes
   */
  val dataSize: Int,
  /**
   * FLV tag timestamp
   */
  val timestamp: Int,
  /**
   * FLV tag stream id, 1 byte
   */
  val streamId: Int,
  /**
   * Filtered tag info
   */
  val filteredInfo: FlvFilteredTagInfo? = null,
) {

  init {
    if (dataSize < 0) {
      throw FlvTagHeaderErrorException("Invalid FLV tag data size: $dataSize")
    }
  }

  fun write(sink: Sink) {
    val buffer = Buffer()
    with(buffer) {
      writeByte(tagType.value and 0x1F)
      // write 3 bytes data size
      writeI24(dataSize)
      // write 3 bytes timestamp
      writeI24(timestamp and 0x00FFFFFF)
      // write 1 byte timestamp extension
      writeByte(((timestamp shr 24).toByte()))
      // write 3 bytes stream id
      writeI24(streamId)
    }
    buffer.transferTo(sink)
    sink.flush()
  }
}