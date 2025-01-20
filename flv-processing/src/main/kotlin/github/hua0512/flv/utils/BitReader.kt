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

package github.hua0512.flv.utils

/**
 * A bit reader that supports reading bits from a byte array
 */
class BitReader(private val data: ByteArray) {
  private var bitPos = 0
  private var bytePos = 0

  fun readBit(): Boolean {
    if (bytePos >= data.size) throw IndexOutOfBoundsException("End of data")

    // Read bit from current byte position
    val bit = (data[bytePos].toInt() and (1 shl (7 - bitPos))) != 0

    // Move to next bit position
    bitPos++
    if (bitPos == 8) {
      bitPos = 0
      bytePos++
    }
    return bit
  }

  fun readBits(n: Int): Int {
    var result = 0
    repeat(n) {
      result = (result shl 1) or (if (readBit()) 1 else 0)
    }
    return result
  }

  fun skipBits(n: Int) {
    repeat(n) {
      readBit()
    }
  }

  /**
   * Read unsigned Exp-Golomb code
   * Based on H.265 9.2 Parsing process for Exp-Golomb codes
   */
  fun readUE(): Int {
    var leadingZeroBits = 0
    var bit = readBit()
    while (!bit) {
      leadingZeroBits++
      if (leadingZeroBits > 31) {
        throw IllegalStateException("Invalid Exp-Golomb code - too many leading zeros")
      }
      bit = readBit()
    }

    var codeNum = 0
    for (i in leadingZeroBits - 1 downTo 0) {
      if (readBit()) {
        codeNum += 1 shl i
      }
    }

    return (1 shl leadingZeroBits) - 1 + codeNum
  }

  /**
   * Read signed Exp-Golomb code
   */
  fun readSE(): Int {
    val value = readUE()
    return if (value == 0) 0 else {
      if (value % 2 == 1) (value + 1) / 2 else -(value / 2)
    }
  }

  /**
   * Debug function to print current state
   */
  fun debugState(): String {
    return "BytePos: $bytePos, BitPos: $bitPos, Current byte: ${if (bytePos < data.size) data[bytePos].toString(2).padStart(8, '0') else "EOF"}"
  }
} 