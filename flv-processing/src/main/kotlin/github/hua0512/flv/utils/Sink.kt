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

package github.hua0512.flv.utils

import kotlinx.io.Buffer
import kotlinx.io.Sink

/**
 * File containing extension functions for OutputStream.
 * @author hua0512
 * @date : 2024/9/15 22:35
 */

/**
 * Writes a 3-byte integer to the OutputStream.
 *
 * @param value The integer value to write.
 */
internal fun Sink.writeI24(value: Int) {
  writeByte(((value shr 16).toByte()))
  writeByte(((value shr 8).toByte()))
  writeByte((value and 0xFF).toByte())
}


internal fun Sink.writeU29(value: Int) {
  val buffer = Buffer()

  with(buffer) {
    var v = value
    while (true) {
      if (v >= 0x80) {
        writeByte(((v and 0x7F) or 0x80).toByte())
        v = v ushr 7
      } else {
        writeByte((v and 0x7F or 0x00).toByte())
        break
      }
    }
  }
  buffer.transferTo(this)
  flush()
}

internal fun Sink.writeUtf8(value: String) {
  val bytes = value.toByteArray(Charsets.UTF_8)
  writeU29(bytes.size shl 1 or 1)
  write(bytes)
}

