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

import github.hua0512.flv.data.video.nal.NalUnit

/**
 * Base class for NAL unit parsing functionality
 */
abstract class BaseNalUnitParser<T : NalUnit> {
  /**
   * Parse a single NAL unit from byte array
   */
  abstract fun parseNalUnit(data: ByteArray): T

  /**
   * Parse multiple NAL units from length-prefixed format
   */
  fun parseNalUnits(data: ByteArray): List<T> {
    val nalUnits = mutableListOf<T>()
    var offset = 0

    while (offset < data.size) {
      // Read NAL unit length (4 bytes)
      if (offset + 4 > data.size) break

      val length = (data[offset].toInt() and 0xFF shl 24) or
              (data[offset + 1].toInt() and 0xFF shl 16) or
              (data[offset + 2].toInt() and 0xFF shl 8) or
              (data[offset + 3].toInt() and 0xFF)

      offset += 4

      // Read NAL unit data
      if (offset + length > data.size) break
      val nalData = data.sliceArray(offset until offset + length)
      nalUnits.add(parseNalUnit(nalData))

      offset += length
    }

    return nalUnits
  }

  /**
   * Parse NAL units from Annex-B format (start code prefixed)
   */
  fun parseAnnexBNalUnits(data: ByteArray): List<T> {
    val nalUnits = mutableListOf<T>()
    var offset = 0

    while (offset < data.size) {
      // Find start code
      val startCodeLength = when {
        data.size >= offset + 4 &&
                data[offset] == 0x00.toByte() &&
                data[offset + 1] == 0x00.toByte() &&
                data[offset + 2] == 0x00.toByte() &&
                data[offset + 3] == 0x01.toByte() -> 4

        data.size >= offset + 3 &&
                data[offset] == 0x00.toByte() &&
                data[offset + 1] == 0x00.toByte() &&
                data[offset + 2] == 0x01.toByte() -> 3

        else -> break
      }

      offset += startCodeLength

      // Find next start code
      val nextOffset = findNextStartCode(data, offset)
      val nalData = data.sliceArray(offset until nextOffset)
      nalUnits.add(parseNalUnit(nalData))

      offset = nextOffset
    }

    return nalUnits
  }


  private fun findNextStartCode(data: ByteArray, offset: Int): Int {
    for (i in offset until data.size - 3) {
      if ((data[i] == 0x00.toByte() &&
                data[i + 1] == 0x00.toByte() &&
                data[i + 2] == 0x01.toByte()) ||
        (data[i] == 0x00.toByte() &&
                data[i + 1] == 0x00.toByte() &&
                data[i + 2] == 0x00.toByte() &&
                data[i + 3] == 0x01.toByte())
      ) {
        return i
      }
    }
    return data.size
  }
} 