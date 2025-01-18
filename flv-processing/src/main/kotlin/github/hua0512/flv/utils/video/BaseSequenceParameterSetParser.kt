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

package github.hua0512.flv.utils.video

import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.utils.logger

/**
 * Base class for Sequence Parameter Set parsing
 */
abstract class BaseSequenceParameterSetParser<T> {
  val logger = logger(this::class.java)

  /**
   * Parse SPS data from RBSP bytes
   */
  abstract fun parse(rbspBytes: ByteArray): T

  /**
   * Remove emulation prevention bytes (0x03) that follow 0x00 0x00
   */
  protected fun removeEmulationPrevention(data: ByteArray): ByteArray {
    val result = ArrayList<Byte>(data.size)
    var i = 0
    while (i < data.size) {
      if (i + 2 < data.size &&
        data[i] == 0x00.toByte() &&
        data[i + 1] == 0x00.toByte() &&
        data[i + 2] == 0x03.toByte() &&
        i + 3 < data.size &&
        data[i + 3].toInt() and 0xFC == 0
      ) {
        // Found emulation prevention pattern, skip the 0x03 byte
        result.add(data[i])
        result.add(data[i + 1])
        i += 3
      } else {
        result.add(data[i])
        i++
      }
    }
    return result.toByteArray()
  }

  /**
   * Validate video dimensions
   */
  protected fun validateDimensions(width: Int, height: Int) {
    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
      throw FlvDataErrorException("Invalid dimensions: ${width}x${height}")
    }
  }

  /**
   * Log byte array in hex format
   */
  protected fun logBytes(prefix: String, bytes: ByteArray) {
    logger.debug("$prefix: ${bytes.joinToString(" ") { "%02x".format(it) }}")
  }
} 