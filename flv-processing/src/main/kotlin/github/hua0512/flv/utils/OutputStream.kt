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

import java.io.OutputStream

/**
 * Writes a 4-byte integer to the OutputStream in big-endian order.
 *
 * @param value The integer value to write.
 */
internal fun OutputStream.writeInt(value: Int) {
  write(value ushr 24)
  write(value ushr 16)
  write(value ushr 8)
  write(value)
}

/**
 * Writes a 2-byte short to the OutputStream in big-endian order.
 *
 * @param value The short value to write.
 */
internal fun OutputStream.writeShort(value: Int) {
  write(value ushr 8)
  write(value)
}

/**
 * Writes an 8-byte double to the OutputStream in big-endian order.
 *
 * @param value The double value to write.
 */
internal fun OutputStream.writeDouble(value: Double) {
  val longBits = java.lang.Double.doubleToLongBits(value)
  write((longBits ushr 56).toInt())
  write((longBits ushr 48).toInt())
  write((longBits ushr 40).toInt())
  write((longBits ushr 32).toInt())
  write((longBits ushr 24).toInt())
  write((longBits ushr 16).toInt())
  write((longBits ushr 8).toInt())
  write(longBits.toInt())
}