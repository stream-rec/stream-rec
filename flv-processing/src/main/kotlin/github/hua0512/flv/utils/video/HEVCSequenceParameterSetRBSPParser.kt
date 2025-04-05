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
 * Parser for HEVC Sequence Parameter Set RBSP data.
 * Based on ITU-T H.265 specification (02/2018) 7.3.2.2.1.
 *
 * **Note:** This parser currently only extracts a subset of fields required by
 * the `HEVCSequenceParameterSet` data class and does *not* parse the full SPS RBSP.
 * It stops after reading the bit depth fields.
 */
object HEVCSequenceParameterSetRBSPParser : BaseSequenceParameterSetParser<HEVCSequenceParameterSet>() {

  override fun parse(rbspBytes: ByteArray): HEVCSequenceParameterSet {
    // Remove emulation prevention bytes first
    val cleanBytes = removeEmulationPrevention(rbspBytes)
    logBytes("Original bytes", rbspBytes)
    logBytes("Clean bytes", cleanBytes)

    val reader = BitReader(cleanBytes)

    try {
      // --- 7.3.2.2.1 Sequence parameter set RBSP syntax ---

      // sps_video_parameter_set_id: u(4)
      val spsVideoParameterSetId = reader.readBits(4)
      // sps_max_sub_layers_minus1: u(3)
      val spsMaxSubLayersMinus1 = reader.readBits(3)
      // sps_temporal_id_nesting_flag: u(1)
      val spsTemporalIdNestingFlag = reader.readBit()

      logger.debug("VPS ID: $spsVideoParameterSetId")
      logger.debug("Max sublayers minus 1: $spsMaxSubLayersMinus1")
      logger.debug("Temporal ID nesting flag: $spsTemporalIdNestingFlag")

      // profile_tier_level( sps_max_sub_layers_minus1, sps_temporal_id_nesting_flag )
      parseProfileTierLevel(reader, spsMaxSubLayersMinus1) // Use helper for clarity

      // sps_seq_parameter_set_id: ue(v)
      val spsSeqParameterSetId = reader.readUE()
      logger.debug("SPS ID: $spsSeqParameterSetId") // Although not returned, useful for debugging

      // chroma_format_idc: ue(v)
      val chromaFormatIdc = reader.readUE()
      logger.debug("Chroma format IDC: $chromaFormatIdc")

      if (chromaFormatIdc == 3) {
        // separate_colour_plane_flag: u(1)
        reader.readBit() // Read and discard
      }

      // pic_width_in_luma_samples: ue(v)
      val picWidthInLumaSamples = reader.readUE()
      logger.debug("Pic width: $picWidthInLumaSamples")

      // pic_height_in_luma_samples: ue(v)
      val picHeightInLumaSamples = reader.readUE()
      logger.debug("Pic height: $picHeightInLumaSamples")

      // Validate dimensions (assuming this function exists and is appropriate)
      validateDimensions(picWidthInLumaSamples, picHeightInLumaSamples)

      // conformance_window_flag: u(1)
      val conformanceWindowFlag = reader.readBit()
      logger.debug("Conformance window flag: $conformanceWindowFlag")

      var confWinLeftOffset = 0
      var confWinRightOffset = 0
      var confWinTopOffset = 0
      var confWinBottomOffset = 0

      if (conformanceWindowFlag) {
        // conf_win_left_offset: ue(v)
        confWinLeftOffset = reader.readUE()
        // conf_win_right_offset: ue(v)
        confWinRightOffset = reader.readUE()
        // conf_win_top_offset: ue(v)
        confWinTopOffset = reader.readUE()
        // conf_win_bottom_offset: ue(v)
        confWinBottomOffset = reader.readUE()
        logger.debug("Conformance window offsets: left=$confWinLeftOffset, right=$confWinRightOffset, top=$confWinTopOffset, bottom=$confWinBottomOffset")
      }

      // bit_depth_luma_minus8: ue(v)
      val bitDepthLumaMinus8 = reader.readUE()
      // bit_depth_chroma_minus8: ue(v)
      val bitDepthChromaMinus8 = reader.readUE()
      logger.debug("Bit depth luma minus 8: $bitDepthLumaMinus8")
      logger.debug("Bit depth chroma minus 8: $bitDepthChromaMinus8")

      // --- ATTENTION: Parsing stops here ---
      // The following fields from the H.265 spec are NOT parsed because they are not
      // currently required by the HEVCSequenceParameterSet data class.
      // This means the reader will not have consumed the entire SPS RBSP.
      // Fields skipped include: log2_max_pic_order_cnt_lsb_minus4, sps_sub_layer_ordering_info_present_flag,
      // scaling_list_enabled_flag, VUI parameters, etc.

      return HEVCSequenceParameterSet(
        picWidthInLumaSamples = picWidthInLumaSamples,
        picHeightInLumaSamples = picHeightInLumaSamples,
        conformanceWindowFlag = conformanceWindowFlag,
        confWinLeftOffset = confWinLeftOffset,
        confWinRightOffset = confWinRightOffset,
        confWinTopOffset = confWinTopOffset,
        confWinBottomOffset = confWinBottomOffset,
        chromaFormatIdc = chromaFormatIdc
        // bitDepthLuma = bitDepthLumaMinus8 + 8, // Could add these if needed
        // bitDepthChroma = bitDepthChromaMinus8 + 8 // Could add these if needed
      )

    } catch (e: IndexOutOfBoundsException) {
      // Catch specific reader exception
      throw FlvDataErrorException("Failed to parse HEVC SPS: Ran out of data. ${e.message}. RBSP bytes: ${rbspBytes.toHex()}")
    } catch (e: IllegalStateException) {
      // Catch specific reader exception (e.g., invalid Exp-Golomb)
      throw FlvDataErrorException("Failed to parse HEVC SPS: Invalid data encountered. ${e.message}. RBSP bytes: ${rbspBytes.toHex()}")
    } catch (e: Exception) {
      // Catch other potential errors
      throw FlvDataErrorException("Failed to parse HEVC SPS: ${e.message}. RBSP bytes: ${rbspBytes.toHex()}")
    }
  }

  /**
   * Parses the profile_tier_level structure according to H.265 spec 7.3.3
   */
  private fun parseProfileTierLevel(reader: BitReader, maxSubLayersMinus1: Int) {
    // --- 7.3.3 Profile, tier and level syntax ---
    // General profile/tier/level information
    val generalProfileSpace = reader.readBits(2)
    val generalTierFlag = reader.readBit()
    val generalProfileIdc = reader.readBits(5)
    logger.debug("General Profile Space: $generalProfileSpace, Tier: $generalTierFlag, IDC: $generalProfileIdc")

    val generalProfileCompatibilityFlags = reader.readBits(32)
    logger.debug(
      "General Profile Compatibility: ${
        generalProfileCompatibilityFlags.toUInt().toString(2).padStart(32, '0')
      }"
    ) // Use UInt for unsigned representation

    // Constraint flags (4 bits) + Reserved (44 bits) = 48 bits total
    // Note: Spec has profile-specific constraint flags within this block for some profiles,
    // but skipping 48 bits covers the total length in common cases.
    // A more precise parser would read the first 4 flags individually.
    reader.skipBits(48)
    logger.debug("Skipped 48 bits for constraint flags and reserved bits")

    val generalLevelIdc = reader.readBits(8)
    logger.debug("General Level IDC: $generalLevelIdc")

    // Sub-layer profile/tier/level information (skipped based on flags)
    skipSubLayerFlags(reader, maxSubLayersMinus1)
  }


  private fun skipSubLayerFlags(reader: BitReader, maxSubLayersMinus1: Int) {
    if (maxSubLayersMinus1 < 0 || maxSubLayersMinus1 > 6) {
      throw FlvDataErrorException("Invalid sps_max_sub_layers_minus1: $maxSubLayersMinus1")
    }

    val subLayerProfilePresentFlag = BooleanArray(maxSubLayersMinus1)
    val subLayerLevelPresentFlag = BooleanArray(maxSubLayersMinus1)

    for (i in 0 until maxSubLayersMinus1) {
      subLayerProfilePresentFlag[i] = reader.readBit() // sub_layer_profile_present_flag[i]
      subLayerLevelPresentFlag[i] = reader.readBit()  // sub_layer_level_present_flag[i]
    }

    if (maxSubLayersMinus1 > 0) {
      for (i in maxSubLayersMinus1 until 8) {
        reader.skipBits(2) // reserved_zero_2bits
      }
    }

    var skippedProfileBits = 0
    var skippedLevelBits = 0
    for (i in 0 until maxSubLayersMinus1) {
      if (subLayerProfilePresentFlag[i]) {
        // sub_layer_profile_space[i] u(2)
        // sub_layer_tier_flag[i] u(1)
        // sub_layer_profile_idc[i] u(5)
        // sub_layer_profile_compatibility_flag[i][32] u(32)
        // sub_layer_progressive_source_flag[i] u(1)
        // sub_layer_interlaced_source_flag[i] u(1)
        // sub_layer_non_packed_constraint_flag[i] u(1)
        // sub_layer_frame_only_constraint_flag[i] u(1)
        // 44 or 43 reserved bits + optional flags = 44 bits typically
        // Total = 2+1+5+32+1+1+1+1+44 = 88 bits
        reader.skipBits(88)
        skippedProfileBits += 88
      }
      if (subLayerLevelPresentFlag[i]) {
        reader.skipBits(8) // sub_layer_level_idc[i] u(8)
        skippedLevelBits += 8
      }
    }
    if (skippedProfileBits > 0 || skippedLevelBits > 0) {
      logger.debug("Skipped sub-layer info: Profile $skippedProfileBits bits, Level $skippedLevelBits bits")
    }
  }

  // Helper extension function for logging byte arrays
  private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }

}