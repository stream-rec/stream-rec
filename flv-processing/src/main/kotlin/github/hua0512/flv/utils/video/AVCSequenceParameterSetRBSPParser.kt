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

import github.hua0512.flv.data.video.avc.nal.SequenceParameterSetData
import java.util.*


// Helper extension function to convert ByteArray to BitSet
private fun ByteArray.toBitSet(): BitSet {
  val bitSet = BitSet(this.size * 8)
  for (i in this.indices) {
    for (j in 0..7) {
      bitSet.set(i * 8 + j, (this[i].toInt() shr (7 - j) and 1) == 1)
    }
  }
  return bitSet
}


internal object AVCSequenceParameterSetRBSPParser {

  fun parse(rbsp: ByteArray): SequenceParameterSetData {
    val bits = rbsp.toBitSet()
    val bitsReader = ExpGolombCodeBitsReader(bits)

    val profileIdc = bitsReader.readBitsAsInt(8)
    val constraintSet0Flag = bitsReader.readBitsAsInt(1)
    val constraintSet1Flag = bitsReader.readBitsAsInt(1)
    val constraintSet2Flag = bitsReader.readBitsAsInt(1)
    val constraintSet3Flag = bitsReader.readBitsAsInt(1)
    val constraintSet4Flag = bitsReader.readBitsAsInt(1)
    val constraintSet5Flag = bitsReader.readBitsAsInt(1)
    val reservedZero2Bits = bitsReader.readBitsAsInt(2)
    require(reservedZero2Bits == 0)
    val levelIdc = bitsReader.readBitsAsInt(8)
    val seqParameterSetId = bitsReader.readUE()

    var chromaFormatIdc = 1
    var separateColourPlaneFlag = 0
    var bitDepthLumaMinus8 = 0
    var bitDepthChromaMinus8 = 0
    var qpprimeYZeroTransformBypassFlag = 0
    var seqScalingMatrixPresentFlag = 0
    val seqScalingListPresentFlag = mutableListOf<Int>()

    if (profileIdc in listOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)) {
      chromaFormatIdc = bitsReader.readUE()
      if (chromaFormatIdc == 3) {
        separateColourPlaneFlag = bitsReader.readBitsAsInt(1)
      }
      bitDepthLumaMinus8 = bitsReader.readUE()
      bitDepthChromaMinus8 = bitsReader.readUE()
      qpprimeYZeroTransformBypassFlag = bitsReader.readBitsAsInt(1)
      seqScalingMatrixPresentFlag = bitsReader.readBitsAsInt(1)
      if (seqScalingMatrixPresentFlag != 0) {
        for (i in 0 until if (chromaFormatIdc != 3) 8 else 12) {
          val flag = bitsReader.readBitsAsInt(1)
          seqScalingListPresentFlag.add(flag)
          if (flag != 0) {
            scalingList(bitsReader, if (i < 6) 16 else 64)
          }
        }
      }
    }

    val log2MaxFrameNumMinus4 = bitsReader.readUE()
    val picOrderCntType = bitsReader.readUE()

    var log2MaxPicOrderCntLsbMinus4 = 0
    var deltaPicOrderAlwaysZeroFlag = 1
    var offsetForNonRefPic = 0
    var offsetForTopToBottomField = 0
    var numRefFramesInPicOrderCntCycle = 0
    val offsetForRefFrame = mutableListOf<Int>()

    when (picOrderCntType) {
      0 -> log2MaxPicOrderCntLsbMinus4 = bitsReader.readUE()
      1 -> {
        deltaPicOrderAlwaysZeroFlag = bitsReader.readBitsAsInt(1)
        offsetForNonRefPic = bitsReader.readSE()
        offsetForTopToBottomField = bitsReader.readSE()
        numRefFramesInPicOrderCntCycle = bitsReader.readUE()
        for (i in 0 until numRefFramesInPicOrderCntCycle) {
          offsetForRefFrame.add(bitsReader.readSE())
        }
      }
    }

    val maxNumRefFrames = bitsReader.readUE()
    val gapsInFrameNumValueAllowedFlag = bitsReader.readBitsAsInt(1)
    val picWidthInMbsMinus1 = bitsReader.readUE()
    val picHeightInMapUnitsMinus1 = bitsReader.readUE()
    val frameMbsOnlyFlag = bitsReader.readBitsAsInt(1)

    var mbAdaptiveFrameFieldFlag = 0
    if (frameMbsOnlyFlag == 0) {
      mbAdaptiveFrameFieldFlag = bitsReader.readBitsAsInt(1)
    }

    val direct8x8InferenceFlag = bitsReader.readBitsAsInt(1)
    val frameCroppingFlag = bitsReader.readBitsAsInt(1)

    var frameCropLeftOffset = 0
    var frameCropRightOffset = 0
    var frameCropTopOffset = 0
    var frameCropBottomOffset = 0

    if (frameCroppingFlag != 0) {
      frameCropLeftOffset = bitsReader.readUE()
      frameCropRightOffset = bitsReader.readUE()
      frameCropTopOffset = bitsReader.readUE()
      frameCropBottomOffset = bitsReader.readUE()
    }

    val vuiParametersPresentFlag = bitsReader.readBitsAsInt(1)

    // vui_parameters ignored

    return SequenceParameterSetData(
      profileIdc,
      constraintSet0Flag,
      constraintSet1Flag,
      constraintSet2Flag,
      constraintSet3Flag,
      constraintSet4Flag,
      constraintSet5Flag,
      levelIdc,
      seqParameterSetId,
      chromaFormatIdc,
      separateColourPlaneFlag,
      bitDepthLumaMinus8,
      bitDepthChromaMinus8,
      qpprimeYZeroTransformBypassFlag,
      seqScalingMatrixPresentFlag,
      seqScalingListPresentFlag,
      log2MaxFrameNumMinus4,
      picOrderCntType,
      log2MaxPicOrderCntLsbMinus4,
      deltaPicOrderAlwaysZeroFlag,
      offsetForNonRefPic,
      offsetForTopToBottomField,
      numRefFramesInPicOrderCntCycle,
      offsetForRefFrame,
      maxNumRefFrames,
      gapsInFrameNumValueAllowedFlag,
      picWidthInMbsMinus1,
      picHeightInMapUnitsMinus1,
      frameMbsOnlyFlag,
      mbAdaptiveFrameFieldFlag,
      direct8x8InferenceFlag,
      frameCroppingFlag,
      frameCropLeftOffset,
      frameCropRightOffset,
      frameCropTopOffset,
      frameCropBottomOffset,
      vuiParametersPresentFlag
    )
  }


  private fun scalingList(bitsReader: ExpGolombCodeBitsReader, listSize: Int) {
    var lastScale = 8
    var nextScale = 8
    for (i in 0 until listSize) {
      if (nextScale != 0) {
        val deltaScale = bitsReader.readSE()
        nextScale = (lastScale + deltaScale + 256) % 256
      }
      if (nextScale != 0) {
        lastScale = nextScale
      }
    }
  }
}