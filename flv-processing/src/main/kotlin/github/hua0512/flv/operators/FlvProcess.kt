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

import github.hua0512.download.DownloadLimitsProvider
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.utils.isEndOfSequence
import github.hua0512.plugins.StreamerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn


/**
 * Process the FLV data flow.
 * @author hua0512
 * @date : 2024/9/10 11:55
 */
fun Flow<FlvData>.process(
  limitsProvider: DownloadLimitsProvider = { 0L to 0.0f },
  context: StreamerContext,
  duplicateTagFiltering: Boolean = true,
): Flow<FlvData> {
  val (fileSizeLimit, durationLimit) = limitsProvider()

  return this.discardFragmented(context)
    .checkHeader(context)
    .split(context)
    .sort(context)
    .filter { !it.isEndOfSequence() }
    .correct(context)
    .fix(context)
//    .concat(context)
    .limit(fileSizeLimit, durationLimit, context)
    .extractJoinPoints(context = context)
    .injectMetadata(context)
    .discardSubsequentScript(context)
    .removeDuplicates(context, enable = duplicateTagFiltering)
    .flowOn(Dispatchers.Default)
}