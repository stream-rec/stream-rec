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
import github.hua0512.flv.data.FlvJoinPoint
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.amf.Amf0Value
import github.hua0512.flv.operators.FlvConcatAction.*
import github.hua0512.flv.utils.ScriptData
import github.hua0512.flv.utils.createMetadataTag
import github.hua0512.flv.utils.isAudioSequenceHeader
import github.hua0512.flv.utils.isHeader
import github.hua0512.flv.utils.isScriptTag
import github.hua0512.flv.utils.isSequenceHeader
import github.hua0512.flv.utils.isTrueScripTag
import github.hua0512.flv.utils.isVideoSequenceHeader
import github.hua0512.flv.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion


enum class FlvConcatAction {
  NOOP,
  CONCAT,
  GATHER,
  CANCEL,
  CORRECT,
  CONCAT_GATHER
}

private const val TAG = "FlvConcatRule"
private val logger = logger(TAG)

private const val NUM_LAST_TAGS = 3
private const val MAX_DURATION = 20_000


/**
 * Concatenates the tags with the same timestamp.
 * @author hua0512
 * @date : 2024/9/6 14:16
 */
internal fun Flow<FlvData>.concat(): Flow<FlvData> = flow {

  var delta = 0L

  var action: FlvConcatAction = NOOP

  val lastTags = mutableListOf<FlvData>()
  val gatheredTags = mutableListOf<FlvData>()

  var lastHeader: FlvHeader? = null
  var lastAudioSequenceTag: FlvData? = null
  var lastVideoSequenceTag: FlvData? = null


  fun reset() {
    delta = 0
    action = NOOP
    lastTags.clear()
    gatheredTags.clear()
    lastHeader = null
    lastAudioSequenceTag = null
    lastVideoSequenceTag = null
  }


  fun updateLastTags(tag: FlvData) {
    lastTags.add(tag)
    if (lastTags.size > NUM_LAST_TAGS) {
      lastTags.removeAt(0)
    }
    if (tag is FlvTag) {
      if (tag.isAudioSequenceHeader()) {
        lastAudioSequenceTag = tag
      } else if (tag.isVideoSequenceHeader()) {
        lastVideoSequenceTag = tag
      }
    }
  }

  fun gatherTags(tag: FlvTag) {
    if (tag.isAudioSequenceHeader()) {
      if (lastAudioSequenceTag == null) {
        logger.debug("Cancel concat due to no last audio sequence header")
        action = CANCEL
      } else {
        if (tag != lastAudioSequenceTag) {
          action = CANCEL
          logger.debug("Cancel concat due to audio sequence header changed")
        }
      }
      lastAudioSequenceTag = tag
    } else if (tag.isVideoSequenceHeader()) {
      if (lastVideoSequenceTag == null) {
        logger.debug("Cancel concat due to no last video sequence header")
        action = CANCEL
      } else {
        if (tag != lastVideoSequenceTag) {
          action = CANCEL
          logger.debug("Cancel concat due to video sequence header changed")
        }
      }
      lastVideoSequenceTag = tag
    }
    gatheredTags.add(tag)
  }

  fun hasGatheringCompleted(): Boolean = (lastTags.last() as FlvTag).header.timestamp >= MAX_DURATION

  fun findLastDuplicatedTag(tags: List<FlvData>): Int {
    logger.debug("Finding duplicated tags...")
    val lastTag = tags.last() as FlvTag
    logger.debug("Last tag: {}", lastTag)

    for ((i, tag) in tags.withIndex()) {
      if (tag != lastTag) {
        continue
      }

      if (tags.subList(maxOf(0, i - (lastTags.size - 1)), i).zip(lastTags.dropLast(1)).all { it.first == it.second }) {
        logger.debug("Last duplicated tag found at index {}, {}", i, tag)
        return i
      }
    }
    logger.debug("No duplicated tag found")
    return -1
  }

  fun updateDeltaDuplicated(tag: FlvTag) {
    delta = (lastTags.last() as FlvTag).header.timestamp - tag.header.timestamp
  }

  fun updateDeltaNonDuplicated(tag: FlvTag) {
    delta = (lastTags.last() as FlvTag).header.timestamp - tag.header.timestamp + 10
  }

  fun correctTs(tag: FlvTag): FlvTag {
    if (delta == 0L) {
      return tag
    }
    return tag.copy(header = tag.header.copy(timestamp = tag.header.timestamp + delta))
  }

  fun makeJoinPointTag(nextTag: FlvTag, seamless: Boolean): FlvTag {
    val joinPoint = FlvJoinPoint(seamless, nextTag.header.timestamp, nextTag.crc32)
    logger.debug("Join point: {}", joinPoint)
    val scriptData = createMetadataTag(nextTag.num - 1, nextTag.header.timestamp, nextTag.header.streamId)
    val data = scriptData.data as ScriptData

    val amfJoinPoint = Amf0Value.Object(
      mapOf(
        "onJoinPoint" to Amf0Value.Object(
          mapOf(
            "seamless" to Amf0Value.Boolean(joinPoint.seamless),
            "timestamp" to Amf0Value.Number(joinPoint.timestamp.toDouble()),
            "crc32" to Amf0Value.Number(joinPoint.crc32.toDouble())
          )
        )
      )
    )

    return scriptData.copy(
      data = data.copy(
        values = listOf(amfJoinPoint)
      )
    )
  }

  suspend fun Flow<FlvData>.doConcat() {
    logger.debug("Concatenating.. gathered {} tags", gatheredTags.size)
    var tags = gatheredTags.filter { (it is FlvTag) && !it.isTrueScripTag() && !it.isSequenceHeader() }
    logger.debug("{} data tags", tags.size)

    if (tags.isEmpty()) return

    val index = findLastDuplicatedTag(tags)
    val seamless = index >= 0
    if (seamless) {
      updateDeltaDuplicated(tags[index] as FlvTag)
      logger.debug("Updated delta : {}, seamless concat", delta)
      tags = tags.subList(index + 1, tags.size)
    } else {
      updateDeltaNonDuplicated(tags[0] as FlvTag)
      logger.debug("Updated delta : {}, non-seamless concat", delta)
    }

    if (tags.isNotEmpty()) {
      val joinPointTag = makeJoinPointTag(correctTs(tags[0] as FlvTag), seamless)
      emit(joinPointTag)
    }

    for (tag in tags) {
      val updatedTag = correctTs(tag as FlvTag)
      updateLastTags(updatedTag)
      emit(updatedTag)
    }
    gatheredTags.clear()
  }


  fun doCancel() = flow {
    logger.debug("Canceling.. gathered {} tags", gatheredTags.size)
    assert(lastHeader != null)
    emit(lastHeader!!)
    for (tag in gatheredTags) {
      updateLastTags(tag)
      emit(tag)
    }
    gatheredTags.clear()
  }


  onCompletion {
    logger.debug("$TAG completed.")
    if (action == GATHER) {
      doConcat()
    }
    reset()
  }.collect { data ->

    if (data.isHeader()) {
      if (lastHeader == null) {
        logger.debug("Nop for the first header")
        lastHeader = data as FlvHeader
        action = NOOP
        emit(data)
      } else {
        logger.debug("Gathering tags for deduplication...")
        lastHeader = data as FlvHeader
        action = if (action == GATHER) {
          CONCAT_GATHER
        } else {
          GATHER
        }
      }
      return@collect
    }

    val tag = data as FlvTag

    while (true) {
      when (action) {
        NOOP -> {
          updateLastTags(tag)
          emit(tag)
          return@collect
        }

        CORRECT -> {
          val updatedTag = correctTs(tag)
          updateLastTags(updatedTag)
          emit(updatedTag)
          return@collect
        }

        CONCAT, CONCAT_GATHER -> {
          doConcat()
          action = if (action == CONCAT_GATHER) GATHER else CORRECT
          if (action == CORRECT) return@collect
        }

        GATHER -> {
          gatherTags(tag)
          if (action == CANCEL) {
            emitAll(doCancel())
            action = NOOP
            return@collect
          }
          if (hasGatheringCompleted()) {
            action = CONCAT
            continue
          }
        }

        else -> break
      }
      break
    }
  }

}