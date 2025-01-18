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

package github.hua0512.flv

import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.sound.FlvSoundFormat
import github.hua0512.flv.data.tag.FlvTagHeader
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.VideoFourCC
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import github.hua0512.flv.utils.AudioData
import github.hua0512.flv.utils.ScriptData
import github.hua0512.flv.utils.VideoData
import io.ktor.utils.io.core.*
import kotlinx.io.Sink

/**
 * FLV writer
 * @author hua0512
 * @date : 2024/6/10 19:16
 */
internal class FlvDumper(val sink: Sink) : AutoCloseable {

  val offset: Int
    get() = sink.size

  fun dumpHeader(header: FlvHeader) {
    header.write(sink)
  }

  fun dumpPreviousTagSize(size: Int) {
    sink.writeInt(size)
  }


  private fun dumpTagHeader(header: FlvTagHeader) {
    header.write(sink)
  }

  fun dumpTag(tag: FlvTag) {
    if (tag.header.timestamp < 0) {
      throw FlvTagHeaderErrorException("Invalid negative timestamp: $tag")
    }
    // write tag header
    dumpTagHeader(tag.header)

    // dump tag data header
    when (tag.data) {
      is AudioData -> tag.data.dump()
      is VideoData -> tag.data.dump()
      is ScriptData -> tag.data.dump()
      else -> throw FlvDataErrorException("Unsupported tag data: ${tag.data}")
    }
    sink.flush()
  }


  private fun AudioData.dump() {
    if (format != FlvSoundFormat.AAC) {
      throw FlvDataErrorException("Unsupported sound format: ${this.format}")
    }
    write(sink)
  }

  private fun VideoData.dump() {
    // Validate codec ID and format
    when {
      codecId == FlvVideoCodecId.AVC -> {
        write(sink)
      }

      codecId == FlvVideoCodecId.HEVC -> {
        write(sink)
      }

      codecId == FlvVideoCodecId.EX_HEADER -> {
        when (fourCC) {
          VideoFourCC.AVC1, VideoFourCC.HVC1 -> write(sink)
          else -> throw FlvDataErrorException("Unsupported video FourCC: $fourCC")
        }
      }

      else -> throw FlvDataErrorException("Unsupported video codec: $codecId")
    }
  }

  private fun ScriptData.dump() {
    write(sink)
  }

  override fun close() {
    with(sink) {
      flush()
      close()
    }
  }

}