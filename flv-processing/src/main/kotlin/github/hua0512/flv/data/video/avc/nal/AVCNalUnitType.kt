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

import github.hua0512.flv.data.video.nal.NalUnitType

// NAL REF IDC codes
private const val NAL_REF_IDC_PRIORITY_HIGHEST = 3
private const val NAL_REF_IDC_PRIORITY_HIGH = 2
private const val NAL_REF_IDC_PRIORITY_LOW = 1
private const val NAL_REF_IDC_PRIORITY_DISPOSABLE = 0

/**
 * AVC NAL unit types as defined in ISO/IEC 14496-10
 */
private const val NAL_UNIT_TYPE_UNSPECIFIED = 0  // Unspecified
private const val NAL_UNIT_TYPE_CODED_SLICE_NON_IDR = 1  // Coded slice of a non-IDR picture
private const val NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_A = 2  // Coded slice data partition A
private const val NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_B = 3  // Coded slice data partition B
private const val NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_C = 4  // Coded slice data partition C
private const val NAL_UNIT_TYPE_CODED_SLICE_IDR = 5  // Coded slice of an IDR picture
private const val NAL_UNIT_TYPE_SEI = 6  // Supplemental enhancement information (SEI)
private const val NAL_UNIT_TYPE_SPS = 7  // Sequence parameter set
private const val NAL_UNIT_TYPE_PPS = 8  // Picture parameter set
private const val NAL_UNIT_TYPE_AUD = 9  // Access unit delimiter
internal const val NAL_UNIT_TYPE_END_OF_SEQUENCE = 10  // End of sequence
private const val NAL_UNIT_TYPE_END_OF_STREAM = 11  // End of stream
private const val NAL_UNIT_TYPE_FILLER = 12  // Filler data
private const val NAL_UNIT_TYPE_SPS_EXT = 13  // Sequence parameter set extension
private const val NAL_UNIT_TYPE_PREFIX_NAL = 14  // Prefix NAL unit
private const val NAL_UNIT_TYPE_SUBSET_SPS = 15  // Subset sequence parameter set
private const val NAL_UNIT_TYPE_AUXILIARY_SLICE = 19  // Coded slice of an auxiliary coded picture without partitioning
private const val NAL_UNIT_TYPE_EXTENSION = 20  // Coded slice extension
private const val NAL_UNIT_TYPE_DEPTH_VIEW = 21  // Coded slice extension for depth view components

sealed class AVCNalUnitType(override val value: Int, override val msg: String) : NalUnitType {

  companion object {
    fun valueOf(value: Int): AVCNalUnitType {
      return when (value) {
        NAL_UNIT_TYPE_UNSPECIFIED -> Unspecified
        NAL_UNIT_TYPE_CODED_SLICE_NON_IDR -> CodedSliceNonIDR
        NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_A -> CodedSliceDataPartitionA
        NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_B -> CodedSliceDataPartitionB
        NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_C -> CodedSliceDataPartitionC
        NAL_UNIT_TYPE_CODED_SLICE_IDR -> CodedSliceIDR
        NAL_UNIT_TYPE_SEI -> SEI
        NAL_UNIT_TYPE_SPS -> SPS
        NAL_UNIT_TYPE_PPS -> PPS
        NAL_UNIT_TYPE_AUD -> AUD
        NAL_UNIT_TYPE_END_OF_SEQUENCE -> EndOfSequence
        NAL_UNIT_TYPE_END_OF_STREAM -> EndOfStream
        NAL_UNIT_TYPE_FILLER -> Filler
        NAL_UNIT_TYPE_SPS_EXT -> SPSExt
        NAL_UNIT_TYPE_PREFIX_NAL -> PrefixNal
        NAL_UNIT_TYPE_SUBSET_SPS -> SubsetSps
        NAL_UNIT_TYPE_AUXILIARY_SLICE -> CodedSliceAux
        NAL_UNIT_TYPE_EXTENSION -> Extension
        NAL_UNIT_TYPE_DEPTH_VIEW -> DepthView
        else -> Unknown(value)
      }
    }
  }

  override fun isVcl(): Boolean {
    return value in NAL_UNIT_TYPE_CODED_SLICE_NON_IDR..NAL_UNIT_TYPE_CODED_SLICE_IDR ||
            value == NAL_UNIT_TYPE_AUXILIARY_SLICE ||
            value == NAL_UNIT_TYPE_EXTENSION
  }

  override fun isIdr(): Boolean = this == CodedSliceIDR

  override fun isSps(): Boolean = this == SPS

  override fun isPps(): Boolean = this == PPS

  data object Unspecified : AVCNalUnitType(NAL_UNIT_TYPE_UNSPECIFIED, "Unspecified")
  data object CodedSliceNonIDR : AVCNalUnitType(NAL_UNIT_TYPE_CODED_SLICE_NON_IDR, "Coded slice of a non-IDR picture")
  data object CodedSliceDataPartitionA : AVCNalUnitType(NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_A, "Coded slice data partition A")
  data object CodedSliceDataPartitionB : AVCNalUnitType(NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_B, "Coded slice data partition B")
  data object CodedSliceDataPartitionC : AVCNalUnitType(NAL_UNIT_TYPE_CODED_SLICE_DATA_PARTITION_C, "Coded slice data partition C")
  data object CodedSliceIDR : AVCNalUnitType(NAL_UNIT_TYPE_CODED_SLICE_IDR, "Coded slice of an IDR picture")
  data object SEI : AVCNalUnitType(NAL_UNIT_TYPE_SEI, "Supplemental enhancement information (SEI)")
  data object SPS : AVCNalUnitType(NAL_UNIT_TYPE_SPS, "Sequence parameter set")
  data object PPS : AVCNalUnitType(NAL_UNIT_TYPE_PPS, "Picture parameter set")
  data object AUD : AVCNalUnitType(NAL_UNIT_TYPE_AUD, "Access unit delimiter")
  data object EndOfSequence : AVCNalUnitType(NAL_UNIT_TYPE_END_OF_SEQUENCE, "End of sequence")
  data object EndOfStream : AVCNalUnitType(NAL_UNIT_TYPE_END_OF_STREAM, "End of stream")
  data object Filler : AVCNalUnitType(NAL_UNIT_TYPE_FILLER, "Filler data")
  data object SPSExt : AVCNalUnitType(NAL_UNIT_TYPE_SPS_EXT, "Sequence parameter set extension")
  data object PrefixNal : AVCNalUnitType(NAL_UNIT_TYPE_PREFIX_NAL, "Prefix NAL unit")
  data object SubsetSps : AVCNalUnitType(NAL_UNIT_TYPE_SUBSET_SPS, "Subset sequence parameter set")
  data object CodedSliceAux : AVCNalUnitType(NAL_UNIT_TYPE_AUXILIARY_SLICE, "Coded slice of an auxiliary coded picture without partitioning")
  data object Extension : AVCNalUnitType(NAL_UNIT_TYPE_EXTENSION, "Coded slice extension")
  data object DepthView : AVCNalUnitType(NAL_UNIT_TYPE_DEPTH_VIEW, "Coded slice extension for depth view components")
  data class Unknown(override val value: Int) : AVCNalUnitType(value, "Unknown")
}

sealed class AVCNalIdcType(open val value: Int) {
  companion object {
    fun valueOf(value: Int): AVCNalIdcType {
      return when (value) {
        NAL_REF_IDC_PRIORITY_HIGHEST -> PriorityHighest
        NAL_REF_IDC_PRIORITY_HIGH -> PriorityHigh
        NAL_REF_IDC_PRIORITY_LOW -> PriorityLow
        NAL_REF_IDC_PRIORITY_DISPOSABLE -> PriorityDisposable
        else -> throw IllegalArgumentException("Unknown NalIdcType value: $value")
      }
    }
  }

  data object PriorityHighest : AVCNalIdcType(NAL_REF_IDC_PRIORITY_HIGHEST)
  data object PriorityHigh : AVCNalIdcType(NAL_REF_IDC_PRIORITY_HIGH)
  data object PriorityLow : AVCNalIdcType(NAL_REF_IDC_PRIORITY_LOW)
  data object PriorityDisposable : AVCNalIdcType(NAL_REF_IDC_PRIORITY_DISPOSABLE)
}

enum class NalStartCode(val bytes: ByteArray) {
  None(bytes = byteArrayOf()),
  Three(bytes = byteArrayOf(0, 0, 1)),
  Four(bytes = byteArrayOf(0, 0, 0, 1))
}
