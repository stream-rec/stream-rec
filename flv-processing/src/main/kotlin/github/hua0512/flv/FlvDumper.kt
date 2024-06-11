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

package github.hua0512.flv

import github.hua0512.flv.FlvParser.Companion.POINTER_SIZE
import github.hua0512.flv.FlvParser.Companion.TAG_HEADER_SIZE
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.sound.FlvSoundFormat
import github.hua0512.flv.data.tag.FlvAudioTagData
import github.hua0512.flv.data.tag.FlvScriptTagData
import github.hua0512.flv.data.tag.FlvTagHeader
import github.hua0512.flv.data.tag.FlvVideoTagData
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import github.hua0512.flv.utils.write3BytesInt
import java.io.DataOutputStream
import java.lang.AutoCloseable

/**
 * FLV writer
 * @author hua0512
 * @date : 2024/6/10 19:16
 */
internal class FlvDumper(val os: DataOutputStream) : AutoCloseable {

  val offset: Int
    get() = os.size()

  fun dumpHeader(header: FlvHeader): Int {
    with(os) {
      // write 'FLV' signature
      write(header.signature.toByteArray())
      writeByte(header.version.toInt())
      writeByte(header.flags.value)
      writeInt(header.headerSize.toInt())
    }
    return header.headerSize.toInt()
  }

  fun dumpPreviousTagSize(size: Int): Int {
    os.writeInt(size)
    return POINTER_SIZE
  }


  private fun dumpTagHeader(header: FlvTagHeader): Int {
    header.write(os)
    return TAG_HEADER_SIZE
  }

  fun dumpTag(tag: FlvTag): Int {
    var bytesWritten = 0
    if (tag.header.timestamp < 0) {
      throw FlvTagHeaderErrorException("Invalid timestamp: ${tag.header.timestamp}")
    }
    // write tag header
    bytesWritten += dumpTagHeader(tag.header)

    // dump tag data header
    bytesWritten += when (tag.data) {
      is FlvAudioTagData -> tag.data.dump()
      is FlvVideoTagData -> tag.data.dump()
      is FlvScriptTagData -> tag.data.dump()
      else -> throw FlvDataErrorException("Unsupported tag data: ${tag.data}")
    }
    // dump body
    // skip if tag data is empty (script tag)
    if (tag.data.binaryData.isNotEmpty()) {
      os.write(tag.data.binaryData)
      bytesWritten += tag.data.binaryData.size
    }
    os.flush()
    return bytesWritten
  }


  private fun FlvAudioTagData.dump(): Int {
    if (format != FlvSoundFormat.AAC) {
      throw FlvDataErrorException("Unsupported sound format: ${this.format}")
    }
    // write info byte
    val info = (format.value shl 4) or (rate.value shl 2) or (size.value shl 1) or type.value
    os.writeByte(info)
    // write aac packet type
    os.writeByte(packetType!!.value)
    return 2
  }

  private fun FlvVideoTagData.dump(): Int {
    // ensure codec id is valid
    // TODO : SUPPORT CHINESE HEVC
    if (codecId != FlvVideoCodecId.AVC) {
      throw FlvDataErrorException("Unsupported video codec id: $codecId")
    }

    // dump flag
    val flag = (frameType.value shl 4) or codecId.value
    os.writeByte(flag)
    // dump avc packet type
    os.writeByte(avcPacketType!!.value)
    // dump composition time
    os.write3BytesInt(compositionTime.toInt())
    return 5
  }

  private fun FlvScriptTagData.dump(): Int {
    write(os)
    return values.sumOf { it.size }
  }

  override fun close() {
    os.flush()
    os.close()
  }

}