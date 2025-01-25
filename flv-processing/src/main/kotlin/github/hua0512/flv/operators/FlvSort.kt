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
import github.hua0512.flv.utils.*
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


private const val TAGS_BUFFER_SIZE = 10
private const val TAG = "FlvSortRule"
private val logger by lazy { logger(TAG) }


/**
 * Extension function to sort a Flow of FlvData.
 *
 * This function processes FLV tags, grouping them into GOP (Group of Pictures) and sorting them
 * based on their type (script, video, audio) and timestamp. It ensures that video sequence headers
 * and audio sequence headers are emitted first if the GOP size is less than the buffer size.
 *
 * @receiver Flow<FlvData> The flow of FLV data to be sorted.
 * @return Flow<FlvData> The sorted flow of FLV data.
 * @author hua0512
 * @date : 2024/9/6 13:57
 */
internal fun Flow<FlvData>.sort(context: StreamerContext): Flow<FlvData> = flow {
  val gopTags = mutableListOf<FlvTag>()


  /**
   * Resets the GOP tags buffer.
   */
  fun reset() {
    gopTags.clear()
  }

  /**
   * Partitions a list of FlvTag into three lists based on their type: script, video, and audio.
   *
   * @receiver List<FlvTag> The list of FLV tags to partition.
   * @return Triple<List<FlvTag>, List<FlvTag>, List<FlvTag>> A triple containing the lists of script, video, and audio tags.
   */
  fun List<FlvTag>.partitionByType(): Triple<List<FlvTag>, List<FlvTag>, List<FlvTag>> {
    val scriptTags = mutableListOf<FlvTag>()
    val videoTags = mutableListOf<FlvTag>()
    val audioTags = mutableListOf<FlvTag>()
    for (tag in this) {
      when {
        tag.isScriptTag() -> scriptTags.add(tag)
        tag.isVideoTag() -> videoTags.add(tag)
        tag.isAudioTag() -> audioTags.add(tag)
      }
    }
    return Triple(scriptTags, videoTags, audioTags)
  }


  /**
   * Emits the GOP tags in a sorted order.
   *
   * This function first checks if the GOP tags buffer is empty. If not, it checks if the buffer size
   * is less than the defined buffer size and emits the video and audio sequence headers first if they exist.
   * Then, it partitions the tags by type and sorts them based on their timestamps before emitting them.
   */
  suspend fun Flow<FlvData>.pushTags() {
    if (gopTags.isEmpty()) {
      return
    }

//    logger.debug("${context.name} Gop tags : {} ", gopTags.size)

    if (gopTags.size < TAGS_BUFFER_SIZE) {
      val avcHeader = gopTags.firstOrNull { it.isVideoSequenceHeader() }
      val aacHeader = gopTags.firstOrNull { it.isAudioSequenceHeader() }

      if (avcHeader != null && aacHeader != null) {
        gopTags.firstOrNull { it.isScriptTag() }?.let { emit(it) }
        emit(avcHeader)
        emit(aacHeader)
        gopTags.clear()
        return
      }
    }

    val (scriptTags, videoTags, audioTags) = gopTags.partitionByType()
    val sortedTags = mutableListOf<FlvTag>()
    var i = audioTags.size - 1

    for (videoTag in videoTags.reversed()) {
      sortedTags.add(0, videoTag)
      while (i >= 0 && audioTags[i].header.timestamp >= videoTag.header.timestamp) {
        sortedTags.add(1, audioTags[i])
        i--
      }
    }

    scriptTags.forEach { emit(it) }
    sortedTags.forEach { emit(it) }
    reset()
  }

  collect { data ->
    if (data.isHeader() || data.isEndOfSequence()) {
      pushTags()
      emit(data)
      logger.debug("${context.name} Reset gop tags...")
      return@collect
    }

    data as FlvTag

    if (data.isNaluKeyFrame()) {
      pushTags()
      gopTags.add(data)
    } else {
      gopTags.add(data)
    }
  }
  pushTags()
  logger.debug("${context.name} completed...")
}
