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

import github.hua0512.flv.data.video.hevc.nal.HEVCDecoderConfigurationRecord
import github.hua0512.flv.data.video.hevc.nal.HEVCParameterSet
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer
import kotlinx.io.readUByte
import kotlinx.io.readUShort


object HEVCSequenceHeaderParser {
  fun parse(data: ByteArray): HEVCDecoderConfigurationRecord {
    val buffer = Buffer().apply { write(data) }

    return buffer.use {
      val configurationVersion = it.readUByte().toInt()

      // Profile tier level parsing
      val profileTierByte = it.readUByte().toInt()
      val generalProfileSpace = (profileTierByte and 0xC0) ushr 6
      val generalTierFlag = ((profileTierByte and 0x20) ushr 5) == 1
      val generalProfileIdc = profileTierByte and 0x1F

      val generalProfileCompatibilityFlags = it.readLong(4)
      val generalConstraintIndicatorFlags = it.readLong(6)

      val generalLevelIdc = it.readUByte().toInt()

      val reserved1 = it.readUShort().toInt()
      val minSpatialSegmentationIdc = reserved1 and 0x0FFF

      val reserved2 = it.readUByte().toInt()
      val parallelismType = reserved2 and 0x03

      val reserved3 = it.readUByte().toInt()
      val chromaFormat = reserved3 and 0x03

      val reserved4 = it.readUByte().toInt()
      val bitDepthLumaMinus8 = reserved4 and 0x07

      val reserved5 = it.readUByte().toInt()
      val bitDepthChromaMinus8 = reserved5 and 0x07

      val avgFrameRate = it.readUShort().toInt()

      val layout = it.readUByte().toInt()
      val constantFrameRate = (layout and 0xC0) ushr 6
      val numTemporalLayers = (layout and 0x38) ushr 3
      val temporalIdNested = ((layout and 0x04) ushr 2) == 1
      val lengthSizeMinusOne = layout and 0x03

      val numOfArrays = it.readUByte().toInt()

      val parameterSets = mutableListOf<HEVCParameterSet>()

      repeat(numOfArrays) { times ->
        val arrayCompleteness = it.readUByte().toInt()
        val nalUnitType = arrayCompleteness and 0x3F
        val numNalus = it.readUShort().toInt()

        val nalUnits = mutableListOf<ByteArray>()
        repeat(numNalus) { nalUnitIndex ->
          val nalUnitLength = it.readUShort().toInt()
          val nalUnit = it.readBytes(nalUnitLength)
          nalUnits.add(nalUnit)
        }

        parameterSets.add(HEVCParameterSet(nalUnitType, nalUnits))
      }

      HEVCDecoderConfigurationRecord(
        configurationVersion,
        generalProfileSpace,
        generalTierFlag,
        generalProfileIdc,
        generalProfileCompatibilityFlags,
        generalConstraintIndicatorFlags,
        generalLevelIdc,
        minSpatialSegmentationIdc,
        parallelismType,
        chromaFormat,
        bitDepthLumaMinus8,
        bitDepthChromaMinus8,
        avgFrameRate,
        constantFrameRate,
        numTemporalLayers,
        temporalIdNested,
        lengthSizeMinusOne,
        numOfArrays,
        parameterSets
      )
    }
  }

  private fun Buffer.readLong(bytes: Int): Long {
    var result = 0L
    repeat(bytes) {
      result = (result shl 8) or readUByte().toLong()
    }
    return result
  }
} 