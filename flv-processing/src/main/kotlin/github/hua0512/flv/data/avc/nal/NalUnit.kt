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

package github.hua0512.flv.data.avc.nal

/***
 *  ISO/IEC 14496-10:2020(E)
 *  7.3.1 NAL unit syntax
 *  7.4.1 NAL unit semantics
 */
data class NalUnit(
  val forbiddenZeroBit: Int,
  val nalRefIdc: NalIdcType,
  val nalUnitType: NalUnitType,
  val rbspBytes: ByteArray,
  val isAnnexB: Boolean,
)

// NAL REF IDC codes
internal const val NAL_REF_IDC_PRIORITY_HIGHEST = 3
internal const val NAL_REF_IDC_PRIORITY_HIGH = 2
internal const val NAL_REF_IDC_PRIORITY_LOW = 1
internal const val NAL_REF_IDC_PRIORITY_DISPOSABLE = 0

// NAL unit type codes
internal const val NAL_UNIT_TYPE_UNSPECIFIED = 0  // Unspecified
internal const val NAL_UNIT_TYPE_CODED_SLICE_NON_IDR = 1  // Coded slice of a non-IDR picture
internal const val NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_A = 2  // Coded slice data partition A
internal const val NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_B = 3  // Coded slice data partition B
internal const val NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_C = 4  // Coded slice data partition C
internal const val NAL_UNIT_TYPE_CODED_SLICE_IDR = 5  // Coded slice of an IDR picture
internal const val NAL_UNIT_TYPE_SEI = 6  // Supplemental enhancement information (SEI)
internal const val NAL_UNIT_TYPE_SPS = 7  // Sequence parameter set
internal const val NAL_UNIT_TYPE_PPS = 8  // Picture parameter set
internal const val NAL_UNIT_TYPE_AUD = 9  // Access unit delimiter
internal const val NAL_UNIT_TYPE_END_OF_SEQUENCE = 10  // End of sequence
internal const val NAL_UNIT_TYPE_END_OF_STREAM = 11  // End of stream
internal const val NAL_UNIT_TYPE_FILLER = 12  // Filler data
internal const val NAL_UNIT_TYPE_SPS_EXT = 13  // Sequence parameter set extension

// 14..18                                          // Reserved
internal const val NAL_UNIT_TYPE_CODED_SLICE_AUX = (
        19  // Coded slice of an auxiliary coded picture without partitioning
        )
// 20..23                                          // Reserved
// 24..31                                          // Unspecified


sealed class NalIdcType(open val value: Int) {
  companion object {
    fun valueOf(value: Int): NalIdcType {
      return when (value) {
        NAL_REF_IDC_PRIORITY_HIGHEST -> NalIdcType.PriorityHighest
        NAL_REF_IDC_PRIORITY_HIGH -> NalIdcType.PriorityHigh
        NAL_REF_IDC_PRIORITY_LOW -> NalIdcType.PriorityLow
        NAL_REF_IDC_PRIORITY_DISPOSABLE -> NalIdcType.PriorityDisposable
        else -> throw IllegalArgumentException("Unknown NalIdcType value: $value")
      }
    }
  }

  data object PriorityHighest : NalIdcType(NAL_REF_IDC_PRIORITY_HIGHEST)
  data object PriorityHigh : NalIdcType(NAL_REF_IDC_PRIORITY_HIGH)
  data object PriorityLow : NalIdcType(NAL_REF_IDC_PRIORITY_LOW)
  data object PriorityDisposable : NalIdcType(NAL_REF_IDC_PRIORITY_DISPOSABLE)
}


sealed class NalUnitType(open val value: Int, val msg: String) {

  companion object {
    fun valueOf(value: Int): NalUnitType {
      return when (value) {
        NAL_UNIT_TYPE_UNSPECIFIED -> NalUnitType.Unspecified
        NAL_UNIT_TYPE_CODED_SLICE_NON_IDR -> NalUnitType.CodedSliceNonIDR
        NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_A -> NalUnitType.CodedSliceDataPartitionA
        NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_B -> NalUnitType.CodedSliceDataPartitionB
        NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_C -> NalUnitType.CodedSliceDataPartitionC
        NAL_UNIT_TYPE_CODED_SLICE_IDR -> NalUnitType.CodedSliceIDR
        NAL_UNIT_TYPE_SEI -> NalUnitType.SEI
        NAL_UNIT_TYPE_SPS -> NalUnitType.SPS
        NAL_UNIT_TYPE_PPS -> NalUnitType.PPS
        NAL_UNIT_TYPE_AUD -> NalUnitType.AUD
        NAL_UNIT_TYPE_END_OF_SEQUENCE -> NalUnitType.EndOfSequence
        NAL_UNIT_TYPE_END_OF_STREAM -> NalUnitType.EndOfStream
        NAL_UNIT_TYPE_FILLER -> NalUnitType.Filler
        NAL_UNIT_TYPE_SPS_EXT -> NalUnitType.SPSExt
        NAL_UNIT_TYPE_CODED_SLICE_AUX -> NalUnitType.CodedSliceAux
        else -> NalUnitType.Unknown(value)
      }
    }
  }


  data object Unspecified : NalUnitType(NAL_UNIT_TYPE_UNSPECIFIED, "Unspecified")
  data object CodedSliceNonIDR : NalUnitType(NAL_UNIT_TYPE_CODED_SLICE_NON_IDR, "Coded slice of a non-IDR picture")
  data object CodedSliceDataPartitionA : NalUnitType(NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_A, "Coded slice data partition A")
  data object CodedSliceDataPartitionB : NalUnitType(NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_B, "Coded slice data partition B")
  data object CodedSliceDataPartitionC : NalUnitType(NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_C, "Coded slice data partition C")
  data object CodedSliceIDR : NalUnitType(NAL_UNIT_TYPE_CODED_SLICE_IDR, "Coded slice of an IDR picture")
  data object SEI : NalUnitType(NAL_UNIT_TYPE_SEI, "Supplemental enhancement information (SEI)")
  data object SPS : NalUnitType(NAL_UNIT_TYPE_SPS, "Sequence parameter set")
  data object PPS : NalUnitType(NAL_UNIT_TYPE_PPS, "Picture parameter set")
  data object AUD : NalUnitType(NAL_UNIT_TYPE_AUD, "Access unit delimiter")
  data object EndOfSequence : NalUnitType(NAL_UNIT_TYPE_END_OF_SEQUENCE, "End of sequence")
  data object EndOfStream : NalUnitType(NAL_UNIT_TYPE_END_OF_STREAM, "End of stream")
  data object Filler : NalUnitType(NAL_UNIT_TYPE_FILLER, "Filler data")
  data object SPSExt : NalUnitType(NAL_UNIT_TYPE_SPS_EXT, "Sequence parameter set extension")
  data object CodedSliceAux : NalUnitType(NAL_UNIT_TYPE_CODED_SLICE_AUX, "Coded slice of an auxiliary coded picture without partitioning")
  data class Unknown(override val value: Int) : NalUnitType(value, "Unknown")
}

enum class NalStartCode(val bytes: ByteArray) {
  None(bytes = byteArrayOf()),
  Three(bytes = byteArrayOf(0, 0, 1)),
  Four(bytes = byteArrayOf(0, 0, 0, 1))
}
