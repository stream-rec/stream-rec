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

import java.util.*

internal class ExpGolombCodeBitsReader(private val bits: BitSet) {

  private var position = 0

  fun readBitsAsInt(numBits: Int): Int {
    var result = 0
    for (i in 0 until numBits) {
      val bit = if (bits.get(position)) 1 else 0
      result = result shl 1 or bit
      position++
    }
    return result
  }

  fun readUE(): Int {
    var leadingZeros = 0
    while (position < bits.size() && !bits.get(position)) {
      leadingZeros++
      position++
    }
    position++ // Skip the leading one
    var value = 0
    for (i in 0 until leadingZeros) {
      value = value shl 1 or readBitsAsInt(1)
    }
    return value + (1 shl leadingZeros) - 1
  }

  fun readSE(): Int {
    val ue = readUE()
    return if (ue % 2 == 0) ue / 2 else -(ue + 1) / 2
  }
}
