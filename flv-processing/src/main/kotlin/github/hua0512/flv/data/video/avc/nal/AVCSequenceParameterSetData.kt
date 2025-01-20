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


/**
 *
 * ISO/IEC 14496-10:2020(E)
 * 7.3.2.1.1 Sequence parameter set data syntax
 * 7.4.2.1.1 Sequence parameter set data semantics

 * Table 6-1 – SubWidthC, and SubHeightC values derived from
 * chroma_format_idc and separate_colour_plane_flag
 */
internal val SUB_WIDTH_HEIGHT_MAPPING = mapOf(
  1 to Pair(2, 2),
  2 to Pair(2, 1),
  3 to Pair(1, 1)
)

/**
 * Sequence parameter set data syntax
 * @author hua0512
 * @date : 2024/6/10 12:57
 */
data class SequenceParameterSetData(
  val profileIdc: Int,
  val constraintSet0Flag: Int,
  val constraintSet1Flag: Int,
  val constraintSet2Flag: Int,
  val constraintSet3Flag: Int,
  val constraintSet4Flag: Int,
  val constraintSet5Flag: Int,
  val levelIdc: Int,
  val seqParameterSetId: Int,
  val chromaFormatIdc: Int = 1,
  val separateColourPlaneFlag: Int = 0,
  val bitDepthLumaMinus8: Int = 0,
  val bitDepthChromaMinus8: Int = 0,
  val qpprimeYZeroTransformBypassFlag: Int = 0,
  val seqScalingMatrixPresentFlag: Int = 0,
  val seqScalingListPresentFlag: List<Int> = listOf(),
  val log2MaxFrameNumMinus4: Int,
  val picOrderCntType: Int,
  val log2MaxPicOrderCntLsbMinus4: Int = 0,
  val deltaPicOrderAlwaysZeroFlag: Int = 1,
  val offsetForNonRefPic: Int = 0,
  val offsetForTopToBottomField: Int = 0,
  val numRefFramesInPicOrderCntCycle: Int = 0,
  val offsetForRefFrame: List<Int> = listOf(),
  val maxNumRefFrames: Int,
  val gapsInFrameNumValueAllowedFlag: Int,
  val picWidthInMbsMinus1: Int,
  val picHeightInMapUnitsMinus1: Int,
  val frameMbsOnlyFlag: Int,
  val mbAdaptiveFrameFieldFlag: Int = 0,
  val direct8x8InferenceFlag: Int,
  val frameCroppingFlag: Int,
  val frameCropLeftOffset: Int = 0,
  val frameCropRightOffset: Int = 0,
  val frameCropTopOffset: Int = 0,
  val frameCropBottomOffset: Int = 0,
  val vuiParametersPresentFlag: Int,
) {
  val chromaArrayType: Int
    get() = if (separateColourPlaneFlag == 0) chromaFormatIdc else 0

  val subWidthC: Int
    get() {
      require(chromaFormatIdc in listOf(1, 2, 3) && separateColourPlaneFlag == 0) { "SubWidthC undefined!" }
      return SUB_WIDTH_HEIGHT_MAPPING[chromaFormatIdc]!!.first
    }

  val subHeightC: Int
    get() {
      require(chromaFormatIdc in listOf(1, 2, 3) && separateColourPlaneFlag == 0) { "SubHeightC undefined!" }
      return SUB_WIDTH_HEIGHT_MAPPING[chromaFormatIdc]!!.second
    }

  val mbWidthC: Int
    get() = if (chromaFormatIdc == 0 || separateColourPlaneFlag == 1) 0 else 16 / subWidthC

  val mbHeightC: Int
    get() = if (chromaFormatIdc == 0 || separateColourPlaneFlag == 1) 0 else 16 / subHeightC

  val picWidthInMbs: Int
    get() = picWidthInMbsMinus1 + 1

  val picWidthInSamplesL: Int
    get() = picWidthInMbs * 16

  val picWidthInSamplesC: Int
    get() = picWidthInMbs * mbWidthC

  val picHeightInMapUnits: Int
    get() = picHeightInMapUnitsMinus1 + 1

  val picSizeInMapUnits: Int
    get() = picWidthInMbs * picHeightInMapUnits

  val frameHeightInMbs: Int
    get() = (2 - frameMbsOnlyFlag) * picHeightInMapUnits

  val cropUnitX: Int
    get() = if (chromaArrayType == 0) 1 else subWidthC

  val cropUnitY: Int
    get() = if (chromaArrayType == 0) 2 - frameMbsOnlyFlag else subHeightC * (2 - frameMbsOnlyFlag)

  val frameWidth: Int
    get() {
      val x0 = cropUnitX * frameCropLeftOffset
      val x1 = picWidthInSamplesL - (cropUnitX * frameCropRightOffset + 1)
      return x1 - x0 + 1
    }

  val frameHeight: Int
    get() {
      val y0 = cropUnitY * frameCropTopOffset
      val y1 = 16 * frameHeightInMbs - (cropUnitY * frameCropBottomOffset + 1)
      return y1 - y0 + 1
    }
}