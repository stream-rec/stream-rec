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

package github.hua0512.flv.data.tag

import github.hua0512.flv.data.sound.AACPacketType
import github.hua0512.flv.data.sound.FlvSoundFormat
import github.hua0512.flv.data.sound.FlvSoundRate
import github.hua0512.flv.data.sound.FlvSoundSize
import github.hua0512.flv.data.sound.FlvSoundType
import java.io.OutputStream

/**
 * A flv audio tag data
 * @author hua0512
 * @date : 2024/6/9 9:30
 */
data class FlvAudioTagData(
  val format: FlvSoundFormat,
  val rate: FlvSoundRate,
  val soundSize: FlvSoundSize,
  val type: FlvSoundType,
  val packetType: AACPacketType?,
  override val binaryData: ByteArray,
) : FlvTagData(binaryData) {

  override fun toString(): String {
    return "FlvMusicTagData(format=$format, rate=$rate, size=$soundSize, type=$type, binaryData=${binaryData.size} bytes)"
  }

  override val headerSize: Int = if (format == FlvSoundFormat.AAC) 2 else 1

  override fun write(os: OutputStream) {
    val info = (format.value shl 4) or (rate.value shl 2) or (soundSize.value shl 1) or type.value
    os.write(info)
    if (format == FlvSoundFormat.AAC) {
      os.write(packetType!!.value.toInt())
    }
    os.write(binaryData)
  }

}