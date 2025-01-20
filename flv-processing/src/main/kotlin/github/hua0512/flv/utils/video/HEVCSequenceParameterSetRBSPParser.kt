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

import github.hua0512.flv.data.video.hevc.HEVCSequenceParameterSet
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.utils.BitReader

/**
 * Parser for HEVC Sequence Parameter Set RBSP data
 * Based on ITU-T H.265 specification (02/2018) 7.3.2.2
 */
object HEVCSequenceParameterSetRBSPParser : BaseSequenceParameterSetParser<HEVCSequenceParameterSet>() {

    override fun parse(rbspBytes: ByteArray): HEVCSequenceParameterSet {
    // Remove emulation prevention bytes first
    val cleanBytes = removeEmulationPrevention(rbspBytes)
        logBytes("Original bytes", rbspBytes)
        logBytes("Clean bytes", cleanBytes)

    val reader = BitReader(cleanBytes)

    try {
      // Parse fixed header values
      val spsVideoParameterSetId = reader.readBits(4)
      val spsMaxSubLayersMinus1 = reader.readBits(3)
      val spsTemporalIdNestingFlag = reader.readBit()

      logger.debug("VPS ID: $spsVideoParameterSetId")
      logger.debug("Max sublayers minus 1: $spsMaxSubLayersMinus1")
      logger.debug("Temporal ID nesting flag: $spsTemporalIdNestingFlag")

      // Parse profile_tier_level
      val generalProfileSpace = reader.readBits(2)
      val generalTierFlag = reader.readBit()
      val generalProfileIdc = reader.readBits(5)
      logger.debug("Profile Space: $generalProfileSpace")
      logger.debug("Tier Flag: $generalTierFlag")
      logger.debug("Profile IDC: $generalProfileIdc")

      // Read profile compatibility flags
      val generalProfileCompatibilityFlags = reader.readBits(32)
      logger.debug("Profile Compatibility: ${generalProfileCompatibilityFlags.toString(2).padStart(32, '0')}")

      // Read constraint flags
      reader.skipBits(48)  // constraint flags and reserved zero bits

      // Read level
      val generalLevelIdc = reader.readBits(8)
      logger.debug("Level IDC: $generalLevelIdc")

      // Skip sublayer flags
      skipSubLayerFlags(reader, spsMaxSubLayersMinus1)

      // Parse SPS ID and other parameters
      val spsSeqParameterSetId = reader.readUE()
      val chromaFormatIdc = reader.readUE()

      logger.debug("SPS ID: $spsSeqParameterSetId")
      logger.debug("Chroma format: $chromaFormatIdc")

      if (chromaFormatIdc == 3) {
        reader.readBit() // separate_colour_plane_flag
      }

      // Picture dimensions - these are unsigned Exp-Golomb coded
      logger.debug("Before reading dimensions: ${reader.debugState()}")
      val picWidthInLumaSamples = reader.readUE()
      logger.debug("Raw width bits: ${String.format("%16s", Integer.toBinaryString(picWidthInLumaSamples)).replace(' ', '0')}")
      logger.debug("After reading width ($picWidthInLumaSamples): ${reader.debugState()}")

      val picHeightInLumaSamples = reader.readUE()
      logger.debug("Raw height bits: ${String.format("%16s", Integer.toBinaryString(picHeightInLumaSamples)).replace(' ', '0')}")
      logger.debug("After reading height ($picHeightInLumaSamples): ${reader.debugState()}")

      // Read bit depth and other parameters
      val bitDepthLumaMinus8 = reader.readUE()
      val bitDepthChromaMinus8 = reader.readUE()
      logger.debug("Bit depth luma: ${bitDepthLumaMinus8 + 8}")
      logger.debug("Bit depth chroma: ${bitDepthChromaMinus8 + 8}")

      // Validate dimensions
        validateDimensions(picWidthInLumaSamples, picHeightInLumaSamples)

      // Conformance window
      val conformanceWindowFlag = reader.readBit()

      var confWinLeftOffset = 0
      var confWinRightOffset = 0
      var confWinTopOffset = 0
      var confWinBottomOffset = 0

      if (conformanceWindowFlag) {
        confWinLeftOffset = reader.readUE()
        confWinRightOffset = reader.readUE()
        confWinTopOffset = reader.readUE()
        confWinBottomOffset = reader.readUE()

        logger.debug("Conformance window offsets: left=$confWinLeftOffset, right=$confWinRightOffset, top=$confWinTopOffset, bottom=$confWinBottomOffset")
      }

      return HEVCSequenceParameterSet(
        picWidthInLumaSamples = picWidthInLumaSamples,
        picHeightInLumaSamples = picHeightInLumaSamples,
        conformanceWindowFlag = conformanceWindowFlag,
        confWinLeftOffset = confWinLeftOffset,
        confWinRightOffset = confWinRightOffset,
        confWinTopOffset = confWinTopOffset,
        confWinBottomOffset = confWinBottomOffset,
        chromaFormatIdc = chromaFormatIdc
      )

    } catch (e: Exception) {
      throw FlvDataErrorException("Failed to parse HEVC SPS: ${e.message}, RBSP bytes: ${rbspBytes.joinToString(" ") { "%02x".format(it) }}")
    }
  }

  private fun skipSubLayerFlags(reader: BitReader, maxSubLayersMinus1: Int) {
    val subLayerProfilePresentFlag = BooleanArray(maxSubLayersMinus1)
    val subLayerLevelPresentFlag = BooleanArray(maxSubLayersMinus1)

    for (i in 0 until maxSubLayersMinus1) {
      subLayerProfilePresentFlag[i] = reader.readBit()
      subLayerLevelPresentFlag[i] = reader.readBit()
    }

    if (maxSubLayersMinus1 > 0) {
      for (i in maxSubLayersMinus1 until 8) {
        reader.skipBits(2) // reserved_zero_2bits
      }
    }

    for (i in 0 until maxSubLayersMinus1) {
      if (subLayerProfilePresentFlag[i]) {
        reader.skipBits(88) // sub_layer_profile_data
      }
      if (subLayerLevelPresentFlag[i]) {
        reader.skipBits(8) // sub_layer_level_idc
      }
    }
  }
} 