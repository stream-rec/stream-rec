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

package github.hua0512.flv.data.tag

import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import github.hua0512.flv.utils.write3BytesInt
import java.io.OutputStream

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
) {

  init {
    if (dataSize < 0) {
      throw FlvTagHeaderErrorException("Invalid FLV tag data size: $dataSize")
    }
  }

  fun write(outputStream: OutputStream) {
    with(outputStream) {
      write(tagType.value.toInt() and 0x1F)
      // write 3 bytes data size
      write3BytesInt(dataSize)
      // write 3 bytes timestamp
      write3BytesInt(timestamp and 0x00FFFFFF)
      // write 1 byte timestamp extension
      write((timestamp shr 24))
      // write 3 bytes stream id
      write3BytesInt(streamId)
    }
  }
}