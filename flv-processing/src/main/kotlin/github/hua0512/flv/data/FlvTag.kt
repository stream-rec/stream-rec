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

package github.hua0512.flv.data

import github.hua0512.flv.FlvParser
import github.hua0512.flv.data.tag.FlvTagData
import github.hua0512.flv.data.tag.FlvTagHeader
import github.hua0512.flv.exceptions.FlvTagHeaderErrorException

/**
 * FLV tag data class
 * @author hua0512
 * @date : 2024/6/9 10:38
 */
data class FlvTag(
  val num: Int = 0,
  val header: FlvTagHeader,
  val data: FlvTagData,
  override val crc32: Long,
) : FlvData {

  override val size
    get() = header.dataSize.toLong() + FlvParser.TAG_HEADER_SIZE

  init {
    if (header.dataSize != data.size) {
      throw FlvTagHeaderErrorException("Data size not match : $this, ${header.dataSize}, ${data.size}")
    }
  }


  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FlvTag) return false
    if (header != other.header) return false
    if (data != other.data) return false
    if (crc32 != other.crc32) return false

    return true
  }

  override fun hashCode(): Int {
    var result = header.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + crc32.hashCode()
    return result
  }
}