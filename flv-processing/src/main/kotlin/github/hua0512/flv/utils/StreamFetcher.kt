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

package github.hua0512.flv.utils

import github.hua0512.flv.FlvReader
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvTag
import github.hua0512.plugins.StreamerContext
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.EOFException


/**
 * Extension function to convert a ByteReadChannel to a Flow of FlvData.
 * This function reads FLV data from the ByteReadChannel and emits it as a Flow of FlvData.
 *
 * @receiver ByteReadChannel The input ByteReadChannel to read FLV data from.
 * @return Flow<FlvData> A Flow emitting FlvData objects read from the ByteReadChannel.
 * @author hua0512
 * @date : 2024/9/10 14:10
 */
fun ByteReadChannel.asStreamFlow(closeSource: Boolean = true, context: StreamerContext): Flow<FlvData> = flow {
  val ins = this@asStreamFlow.toInputStream()

  val flvReader = FlvReader(ins)
  var tag: FlvData? = null

  with(flvReader) {
    try {
      readHeader(::emit)
      readTags {
        tag = it
        emit(it)
      }
      FlvReader.logger.debug("${context.name} End of stream")
    } catch (_: EOFException) {
      // thrown when malformed FLV data is encountered
      // close read and emit end of sequence tag
    } catch (_: SocketTimeoutException) {
      // thrown when the connection is closed
      // close read and emit end of sequence tag
    } catch (e: Exception) {
      e.printStackTrace()
      // other exceptions
      FlvReader.logger.error("${context.name} Exception: ${e.message}")
    } finally {
      if (closeSource) {
        close()
      }
      (tag as? FlvTag)?.let {
        if (it.isAvcEndSequence()) return@let
        emit(
          createEndOfSequenceTag(
            it.num + 1,
            it.header.timestamp + 1,
            it.header.streamId
          )
        )
      }
      tag = null
    }
  }
}