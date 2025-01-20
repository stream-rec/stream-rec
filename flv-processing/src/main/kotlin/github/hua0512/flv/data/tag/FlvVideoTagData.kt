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

import github.hua0512.flv.data.video.*
import github.hua0512.flv.utils.extractResolution
import github.hua0512.flv.utils.isAvcHeader
import github.hua0512.flv.utils.isHevcHeader
import github.hua0512.flv.utils.writeI24
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.serialization.Serializable

/**
 * Flv video tag data
 * @author hua0512
 * @date : 2024/6/9 9:48
 */
@Serializable
data class FlvVideoTagData(
  val frameType: FlvVideoFrameType,
  val codecId: FlvVideoCodecId,
  val compositionTime: Int = 0,
  val packetType: VideoPacketType? = null,
  val fourCC: VideoFourCC? = null,
  val isExHeader: Boolean = false,
  override val binaryData: ByteArray,
) : FlvTagData {

  /**
   * Resolution of a video stream from an AVC sequence header.
   * @return The resolution of the video stream.
   * @throws IllegalArgumentException If the video tag data is not an AVC header or the resolution cannot be extracted.
   * @see isAvcHeader
   */
  val resolution: VideoResolution by lazy {
    require(isAvcHeader() || isHevcHeader())
    require(binaryData.size >= 5)
    val (width, height) = extractResolution(binaryData, codecId)
    VideoResolution(width, height)
  }

  override val headerSize = when (codecId) {
    FlvVideoCodecId.EX_HEADER -> 9
    FlvVideoCodecId.AVC, FlvVideoCodecId.HEVC -> 5
    else -> 1
  }

  override fun write(sink: Sink) {
    val buffer = Buffer()
    with(buffer) {
      if (isExHeader) {
        val info = (frameType.value shl 4) or codecId.value
        writeByte(info.toByte())
        writeInt(fourCC!!.value)
      } else {
        val info = (frameType.value shl 4) or codecId.value
        writeByte(info.toByte())
      }

      if (packetType != null) {
        writeByte(packetType.value.toByte())
        writeI24(compositionTime)
      }
      
      write(binaryData)
    }
    buffer.transferTo(sink)
    sink.flush()
  }

  override fun toString(): String {
    return "FlvVideoTagData(frameType=$frameType, codecId=$codecId, compositionTime=$compositionTime, packetType=$packetType, data=${binaryData.size} bytes)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FlvVideoTagData

    if (compositionTime != other.compositionTime) return false
    if (headerSize != other.headerSize) return false
    if (frameType != other.frameType) return false
    if (codecId != other.codecId) return false
    if (packetType != other.packetType) return false
    if (!binaryData.contentEquals(other.binaryData)) return false
    if (resolution != other.resolution) return false

    return true
  }

  override fun hashCode(): Int {
    var result = compositionTime
    result = 31 * result + headerSize
    result = 31 * result + frameType.hashCode()
    result = 31 * result + codecId.hashCode()
    result = 31 * result + (packetType?.hashCode() ?: 0)
    result = 31 * result + binaryData.contentHashCode()
    result = 31 * result + resolution.hashCode()
    return result
  }
}