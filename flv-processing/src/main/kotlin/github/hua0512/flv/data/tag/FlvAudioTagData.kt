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

package github.hua0512.flv.data.tag

import github.hua0512.flv.data.sound.*
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.serialization.Serializable

/**
 * A flv audio tag data
 * @author hua0512
 * @date : 2024/6/9 9:30
 */
@Serializable
data class FlvAudioTagData(
  val format: FlvSoundFormat,
  val rate: FlvSoundRate,
  val soundSize: FlvSoundSize,
  val type: FlvSoundType,
  val fourCC: AudioFourCC? = null,
  val packetType: AACPacketType? = null,
  override val binaryData: ByteArray,
) : FlvTagData {

  override fun toString(): String {
    return "FlvMusicTagData(format=$format, rate=$rate, size=$soundSize, type=$type, binaryData=${binaryData.size} bytes)"
  }

  override val headerSize: Int = if (format == FlvSoundFormat.AAC) 2 else 1

  override fun write(sink: Sink) {
    val buffer = Buffer()
    with(buffer) {
      val info = (format.value shl 4) or (rate.value shl 2) or (soundSize.value shl 1) or type.value
      writeByte(info.toByte())
      if (format == FlvSoundFormat.AAC) {
        writeByte(packetType!!.value.toByte())
      }
      write(binaryData)
    }
    buffer.transferTo(sink)
    sink.flush()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FlvAudioTagData

    if (headerSize != other.headerSize) return false
    if (format != other.format) return false
    if (rate != other.rate) return false
    if (soundSize != other.soundSize) return false
    if (type != other.type) return false
    if (packetType != other.packetType) return false
    if (!binaryData.contentEquals(other.binaryData)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = headerSize
    result = 31 * result + format.hashCode()
    result = 31 * result + rate.hashCode()
    result = 31 * result + soundSize.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + (packetType?.hashCode() ?: 0)
    result = 31 * result + binaryData.contentHashCode()
    return result
  }

}