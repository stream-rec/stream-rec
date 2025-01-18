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

package github.hua0512.flv.data.video.avc.nal

import github.hua0512.flv.data.video.nal.NalUnit
import github.hua0512.flv.data.video.nal.NalUnitType

data class AVCNalUnit(
  override val nalUnitType: NalUnitType,
  val forbiddenZeroBit: Int,
  val nalRefIdc: AVCNalIdcType,
  override val rbspBytes: ByteArray,
  val isAnnexB: Boolean,
) : NalUnit {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AVCNalUnit

    if (forbiddenZeroBit != other.forbiddenZeroBit) return false
    if (isAnnexB != other.isAnnexB) return false
    if (nalUnitType != other.nalUnitType) return false
    if (nalRefIdc != other.nalRefIdc) return false
    if (!rbspBytes.contentEquals(other.rbspBytes)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = forbiddenZeroBit
    result = 31 * result + isAnnexB.hashCode()
    result = 31 * result + nalUnitType.hashCode()
    result = 31 * result + nalRefIdc.hashCode()
    result = 31 * result + rbspBytes.contentHashCode()
    return result
  }
}