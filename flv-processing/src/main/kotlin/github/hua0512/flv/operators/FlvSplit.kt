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

package github.hua0512.flv.operators

import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.tag.FlvVideoTagData
import github.hua0512.flv.data.video.DecoderConfigurationRecord
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.VideoFourCC
import github.hua0512.flv.data.video.avc.AVCDecoderConfigurationRecord
import github.hua0512.flv.data.video.hevc.nal.HEVCDecoderConfigurationRecord
import github.hua0512.flv.data.video.nal.NalUnit
import github.hua0512.flv.utils.isAudioSequenceHeader
import github.hua0512.flv.utils.isHeader
import github.hua0512.flv.utils.isTrueScripTag
import github.hua0512.flv.utils.isVideoSequenceHeader
import github.hua0512.flv.utils.video.AVCNalUnitParser
import github.hua0512.flv.utils.video.AVCSequenceHeaderParser
import github.hua0512.flv.utils.video.HEVCNalParser
import github.hua0512.flv.utils.video.HEVCSequenceHeaderParser
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import io.exoquery.kmp.pprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


private const val TAG = "FlvSplitRule"
private val logger by lazy { logger(TAG) }


/**
 * Split flv data when avc params are changed
 * @author hua0512
 * @date : 2024/9/7 0:19
 */
internal fun Flow<FlvData>.split(context: StreamerContext): Flow<FlvData> = flow {

  var changed = false // params changed
  var lastHeader: FlvHeader? = null
  var lastMetadata: FlvTag? = null
  var lastAudioSequenceTag: FlvTag? = null
  var lastVideoSequenceTag: FlvTag? = null

  var lastSps: NalUnit? = null
  var lastPps: NalUnit? = null


  fun reset() {
    changed = false
    lastHeader = null
    lastMetadata = null
    lastAudioSequenceTag = null
    lastVideoSequenceTag = null
    lastSps = null
    lastPps = null
  }

  suspend fun insertHeaderAndTags() {
    assert(lastHeader != null)
    emit(lastHeader!!)

    logger.debug("${context.name} re-emit header : {}", lastHeader)
    lastMetadata?.let {
      if (it.num != 1) {
        emit(it.copy(num = 1))
        return@let
      }
      logger.debug("${context.name} re-emit metadata : {}", it)
      emit(it)
    }
    lastVideoSequenceTag?.let {
      emit(it)
      logger.debug("${context.name} re-emit video sequence tag : {}", it)
    }
    lastAudioSequenceTag?.let {
      emit(it)
      logger.debug("${context.name} re-emit audio sequence tag : {}", it)
    }
  }

  suspend fun splitStream() {
    logger.info("${context.name} Splitting stream...")
    insertHeaderAndTags()
    changed = false
    logger.info("${context.name} Stream split")
  }


  fun checkAVCNalUnits(videoData: FlvVideoTagData) {
    // Parse NAL units
    val nalUnits = AVCNalUnitParser.parseNalUnits(videoData.binaryData)

    // Check for SPS and PPS
    val sps = nalUnits.find { it.nalUnitType.isSps() }
    val pps = nalUnits.find { it.nalUnitType.isPps() }

    if (sps != null && pps != null) {
      if (lastSps != null && lastPps != null &&
        (!sps.rbspBytes.contentEquals(lastSps!!.rbspBytes) ||
                !pps.rbspBytes.contentEquals(lastPps!!.rbspBytes))
      ) {
        logger.info("${context.name} SPS/PPS content changed, marking for split")
        changed = true
      }
      lastSps = sps
      lastPps = pps
    }
  }

  fun checkHEVCNalUnits(videoData: FlvVideoTagData) {
    // Parse NAL units
    val nalUnits = HEVCNalParser.parseNalUnits(videoData.binaryData)

    // Check for VPS, SPS and PPS
    val vps = nalUnits.find { it.nalUnitType.isVps() }
    val sps = nalUnits.find { it.nalUnitType.isSps() }
    val pps = nalUnits.find { it.nalUnitType.isPps() }

    if (sps != null && pps != null) {
      if (lastSps != null && lastPps != null &&
        (!sps.rbspBytes.contentEquals(lastSps!!.rbspBytes) ||
                !pps.rbspBytes.contentEquals(lastPps!!.rbspBytes))
      ) {
        logger.info("${context.name} HEVC SPS/PPS content changed, marking for split")
        changed = true
      }
      lastSps = sps
      lastPps = pps
    }
  }


  /**
   * Checks the NAL units within the FLV tag.
   *
   * This function parses the NAL units from the FLV video tag data and performs several checks:
   * - If the stream is in H.264 Annex B format, it ensures that SPS, PPS, and IDR are in the same tag.
   * - It verifies the order of SPS, PPS, and IDR units.
   * - It checks if the global SPS and PPS have changed.
   *
   * If any of these checks fail, it sets the `changed` flag to true.
   */
  suspend fun Flow<FlvData>.checkNalUnits(tag: FlvTag) {
    val videoData = tag.data as FlvVideoTagData

    when {
      videoData.codecId == FlvVideoCodecId.AVC ||
              (videoData.codecId == FlvVideoCodecId.EX_HEADER && videoData.fourCC == VideoFourCC.AVC1) -> {
        checkAVCNalUnits(videoData)
      }

      videoData.codecId == FlvVideoCodecId.HEVC ||
              (videoData.codecId == FlvVideoCodecId.EX_HEADER && videoData.fourCC == VideoFourCC.HVC1) -> {
        checkHEVCNalUnits(videoData)
      }
    }
  }


  fun checkDecoderConfigRecord(record: DecoderConfigurationRecord) {
    when (record) {
      is AVCDecoderConfigurationRecord -> {
        // Get first SPS and PPS from AVC record
        val sps = record.sequenceParameterSets.firstOrNull()?.nalUnits?.firstOrNull()
        val pps = record.pictureParameterSets.firstOrNull()?.nalUnits?.firstOrNull()

        if (sps != null && pps != null) {
          val spsNalUnit = AVCNalUnitParser.parseNalUnit(sps)
          val ppsNalUnit = AVCNalUnitParser.parseNalUnit(pps)

          if (lastSps != null && lastPps != null &&
            (!spsNalUnit.rbspBytes.contentEquals(lastSps!!.rbspBytes) ||
                    !ppsNalUnit.rbspBytes.contentEquals(lastPps!!.rbspBytes))
          ) {
            logger.info("${context.name} AVC decoder config record changed, marking for split")
            changed = true
          }
          lastSps = spsNalUnit
          lastPps = ppsNalUnit
        }
      }

      is HEVCDecoderConfigurationRecord -> {
        // Find SPS (33) and PPS (34) from HEVC parameter sets
        val sps = record.parameterSets.find { it.type == 33 }?.nalUnits?.firstOrNull()
        val pps = record.parameterSets.find { it.type == 34 }?.nalUnits?.firstOrNull()

        if (sps != null && pps != null) {
          val spsNalUnit = HEVCNalParser.parseNalUnit(sps)
          val ppsNalUnit = HEVCNalParser.parseNalUnit(pps)

          if (lastSps != null && lastPps != null &&
            (!spsNalUnit.rbspBytes.contentEquals(lastSps!!.rbspBytes) ||
                    !ppsNalUnit.rbspBytes.contentEquals(lastPps!!.rbspBytes))
          ) {
            logger.info("${context.name} HEVC decoder config record changed, marking for split")
            changed = true
          }
          lastSps = spsNalUnit
          lastPps = ppsNalUnit
        }
      }

      else -> throw IllegalArgumentException("Unsupported decoder configuration record type: ${record::class.simpleName}")
    }
  }

  fun checkVideoSequenceHeader(tag: FlvTag) {
    logger.debug("${context.name} Video sequence tag detected: {}", tag)
    val videoData = tag.data as FlvVideoTagData

    val configRecord = when {
      videoData.codecId == FlvVideoCodecId.AVC ||
              (videoData.codecId == FlvVideoCodecId.EX_HEADER && videoData.fourCC == VideoFourCC.AVC1) -> {
        AVCSequenceHeaderParser.parse(videoData.binaryData)
      }

      videoData.codecId == FlvVideoCodecId.HEVC ||
              (videoData.codecId == FlvVideoCodecId.EX_HEADER && videoData.fourCC == VideoFourCC.HVC1) -> {
        HEVCSequenceHeaderParser.parse(videoData.binaryData)
      }

      else -> throw IllegalArgumentException("Unsupported codec ID: ${videoData.codecId}")
    }

    checkDecoderConfigRecord(configRecord)

    val lastTag = lastVideoSequenceTag
    if (lastTag != null && lastTag.crc32 != tag.crc32) {
      logger.info("${context.name} Video sequence header changed (CRC: {} -> {}), marking for split", lastTag.crc32, tag.crc32)
      changed = true
    }
    lastVideoSequenceTag = tag
  }

  collect {

    if (it.isHeader()) {
      reset()
      lastHeader = it as FlvHeader
      emit(it)
      return@collect
    }

    val tag = it as FlvTag

    when {
      tag.isTrueScripTag() -> {
        if (tag.num == 1) {
          logger.debug("${context.name} Metadata detected: {}", pprint(tag))
        }
        lastMetadata = tag
      }

      tag.isVideoSequenceHeader() -> {
        checkVideoSequenceHeader(tag)
      }

      tag.isAudioSequenceHeader() -> {
        logger.debug("${context.name} Audio sequence tag detected: {}", tag)
        val lastTag = lastAudioSequenceTag
        if (lastTag != null && lastTag.crc32 != tag.crc32) {
          logger.info("${context.name} Audio parameters changed: {} -> {}", lastTag.crc32, tag.crc32)
          changed = true
        }
        lastAudioSequenceTag = tag
      }

      // Flv streams does not typically use Annex B formatted NAL units
//      tag.isNalu() -> checkNalUnits(tag)

      else -> {
        if (changed) {
          splitStream()
        }
      }
    }
    emit(tag)
  }

  logger.debug("${context.name} completed.")
  reset()
}