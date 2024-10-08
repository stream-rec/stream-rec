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
import github.hua0512.flv.data.amf.Amf0Value
import github.hua0512.flv.utils.ScriptData
import github.hua0512.flv.utils.isAudioSequenceHeader
import github.hua0512.flv.utils.isAudioTag
import github.hua0512.flv.utils.isScriptTag
import github.hua0512.flv.utils.isTrueScripTag
import github.hua0512.flv.utils.isVideoSequenceHeader
import github.hua0512.flv.utils.isVideoTag
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.ceil


private const val TAG = "FlvFixRule"
private val logger = logger(TAG)

/**
 * The tolerance for timestamp correction.
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

  var delta: Long = 0
  var lastTag: FlvTag? = null
  var lastAudioTag: FlvTag? = null
  var lastVideoTag: FlvTag? = null
  var frameRate = 30.0
  var videoFrameInterval = (1000 / frameRate)
  var soundSampleInterval = (1000 / 44.1)

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
    videoFrameInterval = (1000 / frameRate)
    soundSampleInterval = (1000 / 44.1)
  }

  /**
   * Calculates the video frame interval based on the given frame rate.
   *
   * @param fps Double The frame rate.
   * @return Double The calculated video frame interval.
   */
  fun calculateVideoFrameInterval(fps: Double) = ceil(1000.0 / fps)

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

    frameRate = if (fps is Amf0Value.Number) fps.value
    else if (fps is Amf0Value.String) fps.value.toDouble()
    else throw IllegalArgumentException("${context.name} Invalid fps type: $fps")

    if (frameRate <= 0) {
      logger.warn("${context.name} Invalid frame rate: $frameRate")
      return
    }
    videoFrameInterval = calculateVideoFrameInterval(frameRate)

    val amfSoundRate = properties["audiosamplerate"] ?: Amf0Value.Number(44100.0)
    val soundRate = (amfSoundRate as Amf0Value.Number).value / 1000
    soundSampleInterval = ceil(1000 / soundRate)

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
      else -> throw IllegalArgumentException("${context.name} Invalid script tag data: $amf")
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
    if (tag.isVideoTag() && lastVideoTag != null) {
      delta = (lastVideoTag!!.header.timestamp - tag.header.timestamp + videoFrameInterval).toLong()
    } else if (tag.isAudioTag() && lastAudioTag != null) {
      delta = (lastAudioTag!!.header.timestamp - tag.header.timestamp + soundSampleInterval).toLong()
    }

    if (lastTag != null && tag.header.timestamp + delta <= lastTag!!.header.timestamp) {
      if (tag.isVideoTag()) {
        delta = (lastTag!!.header.timestamp - tag.header.timestamp + videoFrameInterval).toLong()
      } else if (tag.isAudioTag()) {
        delta = (lastTag!!.header.timestamp - tag.header.timestamp + soundSampleInterval).toLong()
      }
    }
  }

  /**
   * Corrects the timestamp of the given FLV tag.
   *
   * @receiver tag FlvTag The FLV tag to correct.
   * @return FlvTag The FLV tag with the corrected timestamp.
   */
  fun FlvTag.correctTs(delta: Long): FlvTag =
    if (delta == 0L) this
    else copy(header = header.copy(timestamp = header.timestamp + delta))

  /**
   * Checks if the timestamp of the given FLV tag has rebounded.
   *
   * @receiver tag FlvTag The FLV tag to check.
   * @return Boolean True if the timestamp has rebounded, false otherwise.
   */
  fun FlvTag.isTsRebound(): Boolean {
    when {
      isAudioTag() -> {
        if (lastAudioTag == null) return false
        return if (lastAudioTag!!.isAudioSequenceHeader()) this.header.timestamp + delta < lastAudioTag!!.header.timestamp
        else this.header.timestamp + delta <= lastAudioTag!!.header.timestamp
      }

      isVideoTag() -> {
        if (lastVideoTag == null) return false
        return if (lastVideoTag!!.isVideoSequenceHeader()) this.header.timestamp + delta < lastVideoTag!!.header.timestamp
        else this.header.timestamp + delta <= lastVideoTag!!.header.timestamp
      }

      else -> return false
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
    return (header.timestamp + delta - lastTag!!.header.timestamp) > maxOf(soundSampleInterval, videoFrameInterval) + TOLERANCE
  }

  collect { data ->
    if (data is FlvHeader) {
      reset()
      emit(data)
      return@collect
    }

    val tag = data as FlvTag

    if (tag.isScriptTag()) {
      tag.data as ScriptData
      if (tag.isTrueScripTag())
        updateParameters(tag)

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
    val correctedTag = tag.correctTs(delta)
    updateLastTags(correctedTag)
    emit(correctedTag)
  }

  logger.debug("${context.name} completed.")
  reset()
}