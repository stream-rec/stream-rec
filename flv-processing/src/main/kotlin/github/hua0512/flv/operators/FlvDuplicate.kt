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
import github.hua0512.flv.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


private const val NUM_LAST_TAGS = 200
private const val MAX_DURATION = 20_000

private const val TAG = "FlvDuplicateRule"
private val logger = logger(TAG)

/**
 * Simple duplicate elimination rule.
 * This rule eliminates duplicate tags based on the CRC32 value of the tag.
 * The rule keeps track of the last 200 tags and eliminates any tag that has the same CRC32 value as any of the last 200 tags.
 * @receiver Flow<FlvData> The flow of FlvData to process.
 * @return Flow<FlvData> A flow of FlvData with duplicate tags eliminated.
 * @author hua0512
 * @date : 2024/9/17 13:38
 */
internal fun Flow<FlvData>.removeDuplicates(): Flow<FlvData> = flow {
  val lastTags = LinkedHashSet<Long>(NUM_LAST_TAGS)

  fun reset() {
    lastTags.clear()
  }

  collect { flvData ->
    if (lastTags.add(flvData.crc32)) {
      if (lastTags.size > NUM_LAST_TAGS) {
        lastTags.remove(lastTags.iterator().next())
      }
      emit(flvData)
    } else {
      logger.debug("Found duplicate tag: {}", flvData)
    }
  }

  reset()
}