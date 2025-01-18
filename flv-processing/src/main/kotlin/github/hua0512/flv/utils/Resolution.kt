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

import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.VideoResolution
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.utils.video.*

/**
 * Extracts the resolution of a video stream from an AVC/HEVC sequence header.
 *
 * @param packet The packet containing the sequence header.
 * @param codecId The codec ID (AVC or HEVC)
 * @return The resolution of the video stream.
 */
internal fun extractResolution(packet: ByteArray, codecId: FlvVideoCodecId): VideoResolution {
  return when (codecId) {
    FlvVideoCodecId.AVC -> extractAVCResolution(packet)
    FlvVideoCodecId.HEVC -> extractHEVCResolution(packet)
    FlvVideoCodecId.EX_HEADER -> {
      // For enhanced format, we need to skip the FourCC
      val data = packet.copyOfRange(4, packet.size)
      extractHEVCResolution(data)
    }

    else -> throw FlvDataErrorException("Unsupported codec for resolution extraction: $codecId")
  }
}

private fun extractAVCResolution(packet: ByteArray): VideoResolution {
  val record = AVCSequenceHeaderParser.parse(packet)

  // Get first SPS NAL unit
  val spsNalUnit = record.sequenceParameterSets
    .firstOrNull()
    ?.nalUnits
    ?.firstOrNull()
    ?: throw FlvDataErrorException("No SPS found in AVC sequence header")

  // Parse NAL unit and SPS data
  val nalUnit = AVCNalUnitParser.parseNalUnit(spsNalUnit)
  val spsData = AVCSequenceParameterSetRBSPParser.parse(nalUnit.rbspBytes)

  return VideoResolution(spsData.frameWidth, spsData.frameHeight)
}

private fun extractHEVCResolution(packet: ByteArray): VideoResolution {
  val record = HEVCSequenceHeaderParser.parse(packet)

  // Get first VPS, SPS, and PPS NAL units
  val vps = record.parameterSets.find { it.type == 32 /* VPS */ }?.nalUnits?.firstOrNull()
  val sps = record.parameterSets.find { it.type == 33 /* SPS */ }?.nalUnits?.firstOrNull()
    ?: throw FlvDataErrorException("No SPS found in HEVC sequence header")
  val pps = record.parameterSets.find { it.type == 34 /* PPS */ }?.nalUnits?.firstOrNull()
    ?: throw FlvDataErrorException("No PPS found in HEVC sequence header")

  // Parse SPS NAL unit to get resolution
  val spsNalUnit = HEVCNalParser.parseNalUnit(sps)
  val spsData = HEVCSequenceParameterSetRBSPParser.parse(spsNalUnit.rbspBytes)

  val logger = HEVCSequenceParameterSetRBSPParser.logger
  // Log raw dimensions
  logger.debug("HEVC Raw dimensions: ${spsData.picWidthInLumaSamples}x${spsData.picHeightInLumaSamples}")

  // HEVC can have conformance window that affects final dimensions
  val width = spsData.picWidthInLumaSamples
  val height = spsData.picHeightInLumaSamples

  // Apply conformance window if present
  if (spsData.conformanceWindowFlag) {
    // According to HEVC spec, subWidthC and subHeightC are derived from chroma_format_idc
    val subWidthC = if (spsData.chromaFormatIdc == 1 || spsData.chromaFormatIdc == 2) 2 else 1
    val subHeightC = if (spsData.chromaFormatIdc == 1) 2 else 1

    // Calculate cropped dimensions according to spec
    val croppedWidth = width - subWidthC * (spsData.confWinLeftOffset + spsData.confWinRightOffset)
    val croppedHeight = height - subHeightC * (spsData.confWinTopOffset + spsData.confWinBottomOffset)

    logger.debug("HEVC Conformance window:")
    logger.debug("  Left offset: ${spsData.confWinLeftOffset}")
    logger.debug("  Right offset: ${spsData.confWinRightOffset}")
    logger.debug("  Top offset: ${spsData.confWinTopOffset}")
    logger.debug("  Bottom offset: ${spsData.confWinBottomOffset}")
    logger.debug("  Scaling factors: subWidthC=$subWidthC, subHeightC=$subHeightC")
    logger.debug("HEVC Final dimensions: ${croppedWidth}x${croppedHeight}")

    return VideoResolution(croppedWidth, croppedHeight)
  }

  logger.debug("HEVC Final dimensions (no cropping): ${width}x${height}")
  return VideoResolution(width, height)
}