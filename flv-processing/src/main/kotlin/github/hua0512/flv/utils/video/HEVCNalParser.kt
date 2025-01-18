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

import github.hua0512.flv.data.video.hevc.nal.HEVCNalUnit
import github.hua0512.flv.data.video.hevc.nal.HEVCNalUnitType

/**
 * Parser for HEVC NAL units
 */
object HEVCNalParser : BaseNalUnitParser<HEVCNalUnit>() {
  override fun parseNalUnit(data: ByteArray): HEVCNalUnit {
    val firstByte = data[0].toInt() and 0xFF
    val secondByte = data[1].toInt() and 0xFF

    val forbiddenZeroBit = (firstByte and 0x80) ushr 7
    if (forbiddenZeroBit != 0) {
      throw IllegalArgumentException("Forbidden zero bit must be 0")
    }

    val nalUnitType = (firstByte and 0x7E) ushr 1
    val nuhLayerId = ((firstByte and 0x01) shl 5) or ((secondByte and 0xF8) ushr 3)
    val nuhTemporalIdPlus1 = secondByte and 0x07

    // Extract RBSP bytes (skip NAL unit header)
    val rbspBytes = data.sliceArray(2 until data.size)

    return HEVCNalUnit(
      nalUnitType = HEVCNalUnitType.valueOf(nalUnitType),
      nuhLayerId = nuhLayerId,
      nuhTemporalIdPlus1 = nuhTemporalIdPlus1,
      rbspBytes = rbspBytes
    )
  }
}