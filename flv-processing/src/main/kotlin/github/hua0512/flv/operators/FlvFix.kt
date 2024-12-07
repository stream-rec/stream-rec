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
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.amf.AmfValue.Amf0Value
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.utils.*
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.ceil


private const val TAG = "FlvFixRule"
private val logger = logger(TAG)

/**
 * The tolerance for timestamp correction.
 * This value is calculated due to the margin of error by converting from double to int.
 */
private const val TOLERANCE = 1

/**
 * Extension function to fix timestamps in a Flow of FlvData.
 *
 * This function processes FLV tags and corrects their timestamps to ensure proper sequencing.
 * It handles headers, script tags, and sequence headers specifically, and adjusts timestamps
 * for other tags based on the first data tag encountered.
 *
 * @receiver Flow<FlvData> The flow of FLV data to be fixed.
 * @return Flow<FlvData> The fixed flow of FLV data.
 * @author hua0512
 * @date : 2024/9/6 12:46
 */
internal fun Flow<FlvData>.fix(context: StreamerContext): Flow<FlvData> = flow {

  /**
   * Delta, an offset to correct the timestamp.
   */
  var delta: Int = 0
  var lastTag: FlvTag? = null
  var lastAudioTag: FlvTag? = null
  var lastVideoTag: FlvTag? = null
  var frameRate = 30.0
  var videoFrameInterval = ceil(1000 / frameRate).toInt()
  var soundSampleInterval = ceil(1000 / 44.1).toInt()

  /**
   * Resets the correction state.
   *
   * This function clears the delta and resets the last tags and intervals.
   */
  fun reset() {
    delta = 0
    lastTag = null
    lastAudioTag = null
    lastVideoTag = null
    frameRate = 30.0
    videoFrameInterval = ceil(1000 / frameRate).toInt()
    soundSampleInterval = ceil(1000 / 44.1).toInt()
  }

  /**
   * Calculates the video frame interval based on the given frame rate.
   *
   * @param fps Double The frame rate.
   * @return Double The calculated video frame interval.
   */
  fun calculateVideoFrameInterval(fps: Double) = ceil(1000 / fps).toInt()


  /**
   * Calculates the sound sample interval based on the given rate.
   *
   * @param rate Double The sound rate.
   * @return Int The calculated sound sample interval.
   */
  fun calculateSoundSampleInterval(rate: Double) = ceil(1000 / rate).toInt()


  /**
   * Updates the video parameters based on the given properties.
   *
   * This function extracts the frame rate from the properties and updates the video frame interval.
   *
   * @param properties Map<String, Amf0Value> The properties containing the frame rate information.
   */
  fun updateVideoParams(properties: Map<String, Amf0Value>) {
    val fps = properties["fps"] ?: properties["framerate"]
    fps ?: return

    frameRate = when (fps) {
      is Amf0Value.Number -> fps.value
      is Amf0Value.String -> fps.value.toDouble()
      else -> throw FlvDataErrorException("${context.name} Invalid fps type: $fps")
    }

    if (frameRate <= 0) {
      logger.warn("${context.name} Invalid frame rate: $frameRate")
      return
    }
    videoFrameInterval = calculateVideoFrameInterval(frameRate)

    val amfSoundRate = properties["audiosamplerate"]?.run {
      if (this !is Amf0Value.Number) {
        logger.warn("${context.name} Invalid sound rate: $this, using default 44kHz")
        return@run null
      }
      val rate = this.value
      if (rate == 0.0) {
        logger.warn("${context.name} zero sound rate, using default 44kHz")
        // use default sound rate
        return@run null
      }
      if (rate < 5512.0) {
        logger.warn("${context.name} Invalid sound rate: $rate, using default 44kHz")
        return@run null
      }
      this
    } ?: Amf0Value.Number(44100.0)

    // sound rate in kHz
    val soundRate = amfSoundRate.value / 1000
    soundSampleInterval = calculateSoundSampleInterval(soundRate)

    logger.debug("${context.name} fps = $frameRate, videoFrameInterval = $videoFrameInterval, soundSampleInterval = $soundSampleInterval")
  }


  /**
   * Updates the parameters based on the given FLV tag.
   *
   * This function extracts the frame rate from the script tag data and updates the video frame interval.
   *
   * @param tag FlvTag The FLV tag containing the script data.
   */
  fun updateParameters(tag: FlvTag) {
    val amf = (tag.data as ScriptData)[1]
    when (amf) {
      is Amf0Value.Object -> updateVideoParams(amf.properties)
      is Amf0Value.EcmaArray -> updateVideoParams(amf.properties)
      else -> throw FlvDataErrorException("${context.name} Invalid script tag data: $amf")
    }
  }


  /**
   * Updates the last tags based on the given FLV tag.
   *
   * This function updates the last tag, last audio tag, and last video tag based on the type of the given tag.
   *
   * @param tag FlvTag The FLV tag to update the last tags with.
   */
  fun updateLastTags(tag: FlvTag) {
    lastTag = tag
    if (tag.isAudioTag()) {
      lastAudioTag = tag
    } else if (tag.isVideoTag()) {
      lastVideoTag = tag
    }
  }


  /**
   * Updates the delta based on the given FLV tag.
   *
   * This function calculates the delta based on the timestamp of the given tag and the last audio or video tag.
   *
   * @param tag FlvTag The FLV tag to update the delta with.
   */
  fun updateDelta(tag: FlvTag) {
    val current = tag.header.timestamp
    val lastTs = lastTag!!.header.timestamp

    if (tag.isVideoTag() && lastVideoTag != null) {
      delta = (lastVideoTag!!.header.timestamp + videoFrameInterval) - current
    } else if (tag.isAudioTag() && lastAudioTag != null) {
      delta = (lastAudioTag!!.header.timestamp + soundSampleInterval) - current
    }

    val expected = current + delta

    // case when the timestamp is rebounded
    if (lastTag != null && expected <= lastTs) {
      if (tag.isVideoTag()) {
        delta = (lastTs + videoFrameInterval) - current
      } else if (tag.isAudioTag()) {
        delta = (lastTs + soundSampleInterval) - current
      }
    }
  }

  /**
   * Corrects the timestamp of the given FLV tag.
   *
   * @receiver tag FlvTag The FLV tag to correct.
   * @return FlvTag The FLV tag with the corrected timestamp.
   */
  fun FlvTag.correctTs(delta: Int): FlvTag =
    if (delta == 0) this
    else copy(header = header.copy(timestamp = header.timestamp + delta))

  /**
   * Checks if the timestamp of the given FLV tag has rebounded.
   *
   * @receiver tag FlvTag The FLV tag to check.
   * @return Boolean True if the timestamp has rebounded, false otherwise.
   */
  fun FlvTag.isTsRebound(): Boolean {
    val current = this.header.timestamp
    val expected = current + delta

    return when {
      isAudioTag() -> lastAudioTag?.let {
        if (it.isAudioSequenceHeader()) expected < it.header.timestamp
        else expected <= it.header.timestamp
      } ?: false

      isVideoTag() -> lastVideoTag?.let {
        if (it.isVideoSequenceHeader()) expected < it.header.timestamp
        else expected <= it.header.timestamp
      } ?: false

      else -> false
    }
  }

  /**
   * Checks if the timestamp of the given FLV tag is discontinuous.
   *
   * @receiver tag FlvTag The FLV tag to check.
   * @return Boolean True if the timestamp is discontinuous, false otherwise.
   */
  fun FlvTag.isNoncontinuous(): Boolean {
    if (lastTag == null) return false

    // expected timestamp, by applying smoothing
    val expected = header.timestamp + delta
    val diff = expected - lastTag!!.header.timestamp
    val threshold = maxOf(soundSampleInterval, videoFrameInterval) + TOLERANCE
    return diff < 0 || diff > threshold
  }

  collect { data ->
    if (data.isHeader()) {
      reset()
      emit(data)
      return@collect
    }

    val tag = data as FlvTag

    if (tag.isScriptTag()) {
      tag.data as ScriptData
      if (tag.isTrueScripTag()) {
        updateParameters(tag)
      }
      emit(tag)
      return@collect
    }

    if (tag.isTsRebound()) {
      updateDelta(tag)
      logger.warn("${context.name} Timestamp rebounded, updated delta: $delta\nlast tag: $lastTag\nlast video tag: $lastVideoTag\nlast audio tag: $lastAudioTag\ncurrent tag: $tag")
    } else if (tag.isNoncontinuous()) {
      updateDelta(tag)
      logger.warn("${context.name} Timestamp non continuous, updated delta: $delta\nlast tag: $lastTag\nlast video tag: $lastVideoTag\nlast audio tag: $lastAudioTag\ncurrent tag: $tag")
    }
    var correctedTag = tag.correctTs(delta)

    // should never happen
    if (correctedTag.header.timestamp < 0) {
      if (lastTag == null) {
        correctedTag = correctedTag.copy(header = correctedTag.header.copy(timestamp = 0))
        logger.debug("${context.name} negative timestamp: ${correctedTag.header.timestamp}, but no last tag")
      } else {
        val lastTs = lastTag!!.header.timestamp
        if (correctedTag.isVideoTag()) {
          correctedTag = correctedTag.copy(header = correctedTag.header.copy(timestamp = lastTs + videoFrameInterval))
        } else if (correctedTag.isAudioTag()) {
          correctedTag = correctedTag.copy(header = correctedTag.header.copy(timestamp = lastTs + soundSampleInterval))
        }
        logger.debug("${context.name} negative timestamp: ${correctedTag.header.timestamp}, corrected to ${correctedTag.header.timestamp}")
      }
    }

    updateLastTags(correctedTag)
    emit(correctedTag)
  }

  logger.debug("${context.name} completed.")
  reset()
}