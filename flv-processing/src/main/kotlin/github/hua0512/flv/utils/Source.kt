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

package github.hua0512.flv.utils

import github.hua0512.flv.FlvParser
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.VideoFourCC
import github.hua0512.flv.exceptions.FlvErrorException
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Source
import kotlinx.io.readUByte
import java.io.EOFException


/**
 * Extension function to read FLV data from an InputStream and emit it as a Flow of FlvData.
 *
 * This function creates an instance of FlvReader to read the FLV header and tags from the InputStream.
 * The read data is emitted as a Flow of FlvData objects.
 *
 * @receiver InputStream The input stream from which to read the FLV data.
 * @return Flow<FlvData> A flow emitting FlvData objects read from the InputStream.
 * @author hua0512
 * @date : 2024/9/9 12:13
 */
fun Source.asFlvFlow(): Flow<FlvData> = flow {
  val flvReader = FlvParser(this@asFlvFlow)

  var lastTag: FlvData? = null
  var codecId: FlvVideoCodecId? = null
  var videoFourCC: VideoFourCC? = null
  try {
    flvReader.use { reader ->
      reader.readHeader(::emit)
      reader.readTags(disableLogging = true) {
        lastTag = it
        if ((it as FlvTag).isVideoTag()) {
          codecId = (it.data as VideoData).codecId
          videoFourCC = it.data.fourCC
        }
        emit(it)
      }
    }
  } catch (e: EOFException) {
    // End of file reached
  } catch (e: FlvErrorException) {
    e.printStackTrace()
  } catch (e: Exception) {
    if (e !is CancellationException)
      e.printStackTrace()
  } finally {
    lastTag?.let {
      if (it is FlvTag && it.isEndOfSequence()) return@let
      if (it is FlvTag) emit(
        createEndOfSequenceTag(
          it.num + 1,
          it.header.timestamp,
          it.header.streamId,
          codecId = codecId ?: FlvVideoCodecId.AVC,
          fourCC = videoFourCC ?: VideoFourCC.AVC1
        )
      )
    }
    try {
      close()
    } catch (e: Exception) {
      e.printStackTrace()
    }
    lastTag = null
    codecId = null
    videoFourCC = null
  }
}

/**
 * Read an unsigned 24-bit integer from the Source.
 * @receiver Source The source from which to read the unsigned 24-bit integer.
 * @return UInt The unsigned 24-bit integer read from the Source.
 * @author hua0512
 * @date : 2024/6/10 19:31
 */
internal fun Source.readUI24(): UInt {
  require(3)

  // Read 3 bytes from the buffer and convert to int
  val b1 = readUByte().toUInt() shl 16
  val b2 = readUByte().toUInt() shl 8
  val b3 = readUByte().toUInt()
  return b1 or b2 or b3
}