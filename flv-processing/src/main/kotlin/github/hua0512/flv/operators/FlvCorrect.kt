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
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.utils.isHeader
import github.hua0512.flv.utils.isScriptTag
import github.hua0512.flv.utils.isSequenceHeader
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "FlvCorrectRule"
private val logger = logger(TAG)

/**
 * Extension function to correct timestamps in a Flow of FlvData.
 *
 * This function processes FLV tags and corrects their timestamps to ensure proper sequencing.
 * It handles headers, script tags, and sequence headers specifically, and adjusts timestamps
 * for other tags based on the first data tag encountered.
 *
 * @receiver Flow<FlvData> The flow of FLV data to be corrected.
 * @return Flow<FlvData> The corrected flow of FLV data.
 */
internal fun Flow<FlvData>.correct(context: StreamerContext): Flow<FlvData> = flow {
  var delta: Int? = null
  var firstDataTag: FlvTag? = null

  /**
   * Resets the correction state.
   *
   * This function clears the delta and removes the first data tag from the provider.
   */
  fun reset() {
    delta = null
    firstDataTag = null
  }

  /**
   * Corrects the timestamp of a given FLV tag.
   *
   * @param tag FlvTag The FLV tag to correct.
   * @param offset The delta to apply to the timestamp.
   * @return FlvTag The FLV tag with the corrected timestamp.
   */
  fun correctTimestamp(tag: FlvTag, offset: Int): FlvTag {
    if (offset == 0) {
      return tag
    }
    val newTimestamp = tag.header.timestamp + offset
    if (newTimestamp < 0) {
      logger.trace("${context.name} negative timestamp: {} - {} -> {} : {}", tag.header.timestamp, offset, newTimestamp, tag)
    }
    return tag.copy(header = tag.header.copy(timestamp = newTimestamp))
  }

  collect { item ->
    if (item.isHeader()) {
      reset()
      emit(item)
      return@collect
    }

    item as FlvTag

    if (item.isScriptTag()) {
      val scriptTag = when {
        // first script tag must have timestamp 0
        item.header.timestamp != 0 && item.num == 1 -> correctTimestamp(item, -item.header.timestamp)
        item.header.timestamp != 0 -> {
//          logger.debug("${context.name} received extra script tag: {}", item)
          correctTimestamp(item, delta ?: -item.header.timestamp)
        }

        else -> correctTimestamp(item, delta ?: -item.header.timestamp)
      }
      emit(scriptTag)
      return@collect
    }

    if (delta == null) {
      if (item.isSequenceHeader()) {
        // Sequence timestamp must be 0
        if (item.header.timestamp != 0) {
          val sequenceTag = correctTimestamp(item, -item.header.timestamp)
          // SHOULD NEVER HAPPEN
          if (sequenceTag.header.timestamp < 0) {
            logger.debug("{} detected failed correction: {}", context.name, sequenceTag)
            emit(item.copy(header = item.header.copy(timestamp = 0)))
          } else {
            emit(sequenceTag)
          }
        } else {
          emit(item)
        }
      } else {
        if (firstDataTag == null) {
          firstDataTag = item
          logger.debug("${context.name} The first data tag: {}", item)
        } else {
          logger.debug("${context.name} The second data tag: {}", item)
          if (item.header.timestamp >= firstDataTag!!.header.timestamp) {
            delta = -firstDataTag!!.header.timestamp
            logger.debug("${context.name} success ts greater than first tag, $delta")
            emit(correctTimestamp(firstDataTag!!, delta!!))
            emit(correctTimestamp(item, delta!!))
          } else {
            delta = -item.header.timestamp
            logger.debug("${context.name} first ts greater than second tag, $delta")
            emit(correctTimestamp(item, delta!!))
            emit(correctTimestamp(firstDataTag!!, delta!!))
          }
          firstDataTag = null
        }
      }
      return@collect
    }

    // Rebound timestamp by delta to ensure proper sequencing
    emit(correctTimestamp(item, delta!!))
  }

  logger.debug("${context.name} completed")
  reset()
}