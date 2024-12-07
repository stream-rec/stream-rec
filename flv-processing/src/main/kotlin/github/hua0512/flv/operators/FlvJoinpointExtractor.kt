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
import github.hua0512.flv.data.FlvJoinPoint
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.amf.AmfValue.Amf0Value
import github.hua0512.flv.utils.ScriptData
import github.hua0512.flv.utils.isHeader
import github.hua0512.flv.utils.isScriptTag
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


typealias onJoinPointExtracted = (List<FlvJoinPoint>) -> Unit

private const val TAG = "FlvJoinPointExtractor"
private val logger = logger(TAG)


/**
 * Extract join points from the FLV data flow.
 * @author hua0512
 * @date : 2024/9/12 1:07
 */
internal fun Flow<FlvData>.extractJoinPoints(onExtracted: onJoinPointExtracted? = null, context: StreamerContext): Flow<FlvData> = flow {

  val joinPoints = mutableListOf<FlvJoinPoint>()
  var joinPointTag: FlvTag? = null

  var streamIndex = -1


  fun reset() {
    streamIndex = -1
    joinPoints.clear()
    joinPointTag = null
  }

  fun Flow<FlvData>.pushJoinPoint() {
    onExtracted?.invoke(joinPoints.toList())
  }

  collect { value ->

    if (value.isHeader()) {
      streamIndex++
      if (streamIndex > 0) {
        pushJoinPoint()
        joinPoints.clear()
      }
      joinPointTag = null
      emit(value)
      return@collect
    }

    value as FlvTag

    joinPointTag?.let { tag ->
      val joinPoint = makeJoinPoint(tag, value, context)
      joinPoints.add(joinPoint)
      joinPointTag = null
    }

    if (value.isScriptTag()) {
      val scriptData = value.data as ScriptData
      if (scriptData[0] is Amf0Value.String) {
        val name = (scriptData[0] as Amf0Value.String).value
        if (name == "onJoinPoint") {
          joinPointTag = value
          return@collect
        }
      }
    }

    emit(value)
  }

  pushJoinPoint()
  reset()
  logger.debug("${context.name} completed")
}


private fun makeJoinPoint(joinPointTag: FlvTag, nextTag: FlvTag, context: StreamerContext): FlvJoinPoint {

  val joinPointData = joinPointTag.data as ScriptData
  val joinPointProps = (joinPointData[1] as Amf0Value.Object).properties

  val joinPoint = FlvJoinPoint(
    seamless = (joinPointProps["seamless"] as Amf0Value.Boolean).value,
    timestamp = (joinPointProps["timestamp"] as Amf0Value.Number).value.toInt(),
    crc32 = (joinPointProps["crc32"] as Amf0Value.Number).value.toLong()
  )

  logger.debug("${context.name} Join point: {}, next tag: {}", joinPoint, nextTag)
  if (nextTag.crc32 != joinPoint.crc32) {
    logger.warn("${context.name} Join point crc32 mismatch, expected: {}, actual: {}", joinPoint.crc32, nextTag.crc32)
  }

  return joinPoint

}