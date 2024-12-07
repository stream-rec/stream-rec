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

import github.hua0512.flv.FlvAnalyzer
import github.hua0512.flv.FlvMetaInfoProvider
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.utils.isHeader
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import io.exoquery.kmp.pprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

private const val TAG = "FlvAnalyzerRule"

private val logger = logger(TAG)


/**
 * Extension function to analyze a flow of `FlvData` objects.
 *
 * This function processes a stream of `FlvData` objects, analyzing headers and tags,
 * and updating metadata information using the provided `FlvMetaInfoProvider` and `FlvAnalyzerSizedUpdater`.
 *
 * @receiver Flow<FlvData> The flow of `FlvData` objects to be analyzed.
 * @param infoProvider FlvMetaInfoProvider The provider for metadata information.
 * @return Flow<FlvData> The analyzed flow of `FlvData` objects.
 * @author hua0512
 * @date : 2024/9/8 21:03
 */
fun Flow<FlvData>.analyze(infoProvider: FlvMetaInfoProvider, context: StreamerContext): Flow<FlvData> = flow {

  // Index of the current stream being processed
  var streamIndex = -1
  // Instance of FlvAnalyzer to perform the analysis
  val analyzer = FlvAnalyzer(context)

  // Resets the analyzer state
  fun reset() {
    analyzer.reset()
  }

  // Pushes the current metadata information to the infoProvider
  fun pushMetadata() {
    if (streamIndex < 0) {
      return
    }
    val metadataInfo = analyzer.makeMetaInfo()
    logger.info("${context.name} push[{}]: {}", streamIndex, pprint(metadataInfo, defaultHeight = 50))
    infoProvider[streamIndex] = metadataInfo
  }

  // Collects and processes each FlvData object in the flow
  onCompletion {
    logger.debug("${context.name} completed analysis : {}", streamIndex, it)
    // Push the final metadata information and reset the analyzer
    pushMetadata()
    reset()
    streamIndex = -1
  }.collect { value ->
    if (value.isHeader()) {
      if (streamIndex == -1) {
        // do nothing
        logger.debug("${context.name} first header initialized")
      } else {
        // Push the metadata information and reset the analyzer
        pushMetadata()
        reset()
      }
      // Increment the stream index
      streamIndex++
      // Analyze the header
      analyzer.analyzeHeader(value as FlvHeader)
    } else {
      // Analyze the tag
      analyzer.analyzeTag(value as FlvTag)
    }
    // Emit the value
    emit(value)
  }
}