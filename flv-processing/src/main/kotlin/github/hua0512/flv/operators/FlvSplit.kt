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

package github.hua0512.flv.operators

import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.avc.AVCSequenceHeaderParser
import github.hua0512.flv.data.avc.nal.NalUnit
import github.hua0512.flv.data.avc.nal.NalUnitParser
import github.hua0512.flv.data.avc.nal.NalUnitType
import github.hua0512.flv.utils.isAudioSequenceHeader
import github.hua0512.flv.utils.isHeader
import github.hua0512.flv.utils.isTrueScripTag
import github.hua0512.flv.utils.isVideoSequenceHeader
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import io.exoquery.pprint
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

  suspend fun Flow<FlvData>.insertHeaderAndTags() {
    assert(lastHeader != null)
    emit(lastHeader!!)

    lastMetadata?.let {
      emit(it)
    }
    lastVideoSequenceTag?.let {
      emit(it)
    }
    lastAudioSequenceTag?.let {
      emit(it)
    }
  }

  suspend fun Flow<FlvData>.splitStream() {
    logger.debug("${context.name} Splitting stream...")
    changed = false
    insertHeaderAndTags()
    logger.debug("${context.name} Stream split")
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
    val nalUnits = NalUnitParser.parseFromH264(tag.data.binaryData)
    val idrNalUnit = nalUnits.withIndex().firstOrNull { it.value.nalUnitType == NalUnitType.CodedSliceIDR }
    if (idrNalUnit != null) {
      // TODO : Consider to add a flag option to enable/disable this check
      // check if its H.264 Annex B stream format
      if (idrNalUnit.value.isAnnexB) {
        // H.264 Annex B stream format do not need to split if SPS, PPS, IDR are in the same tag
        val sps = nalUnits.withIndex().find { it.value.nalUnitType == NalUnitType.SPS }
        val pps = nalUnits.withIndex().find { it.value.nalUnitType == NalUnitType.PPS }
        if (sps != null && pps != null) {

          // Check if global SPS and PPS are available
          // Should be the same as the global SPS and PPS
          if (lastSps != null && lastPps != null) {
            if (sps.value != lastSps || pps.value != lastPps) {
              // Annex B format has the ability to decode independently even if SPS, PPS differ from global SPS, PPS
              // do not need to split the stream
              logger.debug("${context.name}  Nalu SPS or PPS differ from global...")
            }
          }

          // check if SPS, PPS, IDR order
          // normally SPS should be before PPS, and PPS should be before IDR
          if (sps.index >= pps.index || pps.index >= idrNalUnit.index) {
            logger.debug("${context.name}  Nalu SPS, PPS, IDR order is incorrect...")
            splitStream()
          }

        } else {
          logger.debug("${context.name} Nalu SPS and PPS not detected...")
          // rare case, there is no SPS, PPS
          // TODO : should we insert the global SPS, PPS here? or just split the stream?
          splitStream()
        }
      }
    }
  }



  collect {

    if (it.isHeader()) {
      reset()
      emit(it)
      return@collect
    }

    val tag = it as FlvTag

    when {
      tag.isTrueScripTag() -> {
        logger.debug("${context.name} Metadata detected: {}", pprint(tag))
        lastMetadata = tag
      }

      tag.isVideoSequenceHeader() -> {
        logger.debug("${context.name} Video sequence tag detected: {}", tag)

        val avcHeader = AVCSequenceHeaderParser.parse(tag.data.binaryData)
        var sps: NalUnit? = null
        var pps: NalUnit? = null

        // should be 1 SPS and 1 PPS
        if (avcHeader.numOfSequenceParameterSets > 0) {
          // get the first SPS
          sps = NalUnitParser.parseNalUnit(avcHeader.sequenceParameterSets.first().nalUnit)
        }
        if (avcHeader.numOfPictureParameterSets > 0) {
          // get the first PPS
          pps = NalUnitParser.parseNalUnit(avcHeader.pictureParameterSets.first().nalUnit)
        }

        val lastTag = lastVideoSequenceTag
        // check if last video sequence tag is not null and not the same as the current tag
        if (lastTag != null && lastTag.crc32 != tag.crc32) {
          logger.debug("${context.name} Video parameters changed: {} -> {}", lastTag.crc32, tag.crc32)
          changed = true
          // Flv streams does not typically use Annex B formatted NAL units
//          if (lastSps != null && lastPps != null) {
//            // check if SPS, PPS are available
//            if (sps == null || pps == null) { // rare case, there is no SPS, PPS
//              logger.debug("Global SPS or PPS not detected...")
//              changed = true
//            } else if (lastSps != sps || lastPps != pps) { // check if SPS, PPS changed
//              logger.debug("Global SPS or PPS changed...")
//              // Check later if the stream is in H.264 Annex B format
//              if (!sps.isAnnexB)
//                changed = true
//            } else {
//              logger.debug("Global SPS and PPS not changed...")
//            }
//          } else {
//            logger.debug("Video parameters changed...")
//            changed = true
//          }
        }
        lastSps = sps
        lastPps = pps
        logger.debug("${context.name} SPS : {}", lastSps)
        logger.debug("${context.name} PPS : {}", lastPps)
        lastVideoSequenceTag = tag
      }

      tag.isAudioSequenceHeader() -> {
        logger.debug("${context.name} Audio sequence tag detected: {}", tag)
        val lastTag = lastAudioSequenceTag
        if (lastTag != null && lastTag.crc32 != tag.crc32) {
          logger.debug("${context.name} Audio parameters changed : {} -> {}", lastTag.crc32, tag.crc32)
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