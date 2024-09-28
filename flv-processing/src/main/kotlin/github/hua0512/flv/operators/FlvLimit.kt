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

import github.hua0512.flv.FlvParser
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.utils.isAudioSequenceHeader
import github.hua0512.flv.utils.isHeader
import github.hua0512.flv.utils.isNaluKeyFrame
import github.hua0512.flv.utils.isTrueScripTag
import github.hua0512.flv.utils.isVideoSequenceHeader
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


private const val TAG = "FlvLimitRule"
private val logger = logger(TAG)

/**
 * Limits the file size and duration of the flv file.
 * @author hua0512
 * @date : 2024/9/7 0:02
 */
internal fun Flow<FlvData>.limit(fileSizeLimit: Long = 0, durationLimit: Float = 0.0f, context: StreamerContext): Flow<FlvData> = flow {

  var duration: Float = 0.0f
  var fileSize = 0L

  var lastHeader: FlvHeader? = null
  var lastMetadata: FlvTag? = null
  var lastAudioSequenceTag: FlvTag? = null
  var lastVideoSequenceTag: FlvTag? = null

  var firstKeyFrameTag: FlvTag? = null
  var lastKeyFrameTag: FlvTag? = null

  var maxSizeBetweenKeyFrames: Int = 0
  var maxDurationBetweenKeyFrames: Float = 0.0f

  fun reset() {
    duration = 0.0f
    fileSize = 0L
    lastHeader = null
    lastMetadata = null
    lastAudioSequenceTag = null
    lastVideoSequenceTag = null
    firstKeyFrameTag = null
    lastKeyFrameTag = null
    maxSizeBetweenKeyFrames = 0
    maxDurationBetweenKeyFrames = 0.0f
  }

  fun updateLastData(data: FlvTag) {
    // check if the tag is a metadata tag
    if (data.isTrueScripTag()) {
      lastMetadata = data
    } else if (data.isAudioSequenceHeader()) {
      lastAudioSequenceTag = data
    } else if (data.isVideoSequenceHeader()) {
      lastVideoSequenceTag = data
    }
  }

  suspend fun Flow<FlvData>.insertHeaderAndTags() {
    assert(lastHeader != null)
    emit(lastHeader!!)

    lastMetadata?.let {
      // ensure the metadata tag num is 1
      emit(it.copy(num = 1))
    }
    lastVideoSequenceTag?.let {
      emit(it)
    }
    lastAudioSequenceTag?.let {
      emit(it)
    }

    fileSize = lastHeader!!.size.toLong()
    if (lastMetadata != null) {
      fileSize += lastMetadata!!.size.toLong()
    }
    if (lastVideoSequenceTag != null) {
      fileSize += lastVideoSequenceTag!!.size.toLong()
    }
    if (lastAudioSequenceTag != null) {
      fileSize += lastAudioSequenceTag!!.size.toLong()
    }
    duration = 0.0f
    firstKeyFrameTag = lastKeyFrameTag
  }

  fun isFileSizeLimitReached(): Boolean {
    return fileSize + maxSizeBetweenKeyFrames >= fileSizeLimit
  }

  fun isDurationLimitReached(): Boolean {
    return duration + maxDurationBetweenKeyFrames >= durationLimit
  }


  fun isLimitReached(data: FlvTag): Boolean {
    // update the duration and file size
    fileSize += data.size.toLong() + FlvParser.POINTER_SIZE

    // check if its video nalu packet
    if (!data.isNaluKeyFrame()) {
      return false
    }

    // check if the first key frame is initialized
    if (firstKeyFrameTag == null) {
      firstKeyFrameTag = data
    }

    if (lastKeyFrameTag != null) {
      maxDurationBetweenKeyFrames = maxOf(maxDurationBetweenKeyFrames, (data.header.timestamp - lastKeyFrameTag!!.header.timestamp).toFloat() / 1000)
      maxSizeBetweenKeyFrames = maxOf(maxSizeBetweenKeyFrames, lastKeyFrameTag!!.size.toInt())
    }

    lastKeyFrameTag = data
    duration = (lastKeyFrameTag!!.header.timestamp - firstKeyFrameTag!!.header.timestamp) / 1000.0f


    if (fileSizeLimit > 0 && isFileSizeLimitReached()) {
      logger.info("${context.name} File size limit reached : $fileSize")
      return true
    }
    if (durationLimit > 0 && isDurationLimitReached()) {
      logger.info("${context.name} Duration limit reached : $duration")
      return true
    }

    return false

  }

  collect {
    if (it.isHeader()) {
      reset()
      fileSize += (it as FlvHeader).size + FlvParser.POINTER_SIZE
      lastHeader = it
    } else {
      updateLastData(it as FlvTag)
      val limitReached = isLimitReached(it)
      // if the limit is reached, insert the header, metadata and global sequence tags
      if (limitReached) {
        insertHeaderAndTags()
      }
    }
    emit(it)
  }

  reset()
  logger.debug("${context.name} completed")
}.correct(context)