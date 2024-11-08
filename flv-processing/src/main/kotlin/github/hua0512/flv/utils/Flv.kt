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

import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.amf.Amf0Value
import github.hua0512.flv.data.amf.Amf0Value.*
import github.hua0512.flv.data.amf.Amf0Value.Number
import github.hua0512.flv.data.amf.Amf0Value.String
import github.hua0512.flv.data.avc.AvcPacketType
import github.hua0512.flv.data.other.FlvKeyframe
import github.hua0512.flv.data.tag.*
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.FlvVideoFrameType
import github.hua0512.utils.crc32

/**
 * @author hua0512
 * @date : 2024/8/2 21:58
 */

internal typealias Keyframe = FlvKeyframe
internal typealias VideoData = FlvVideoTagData
internal typealias AudioData = FlvAudioTagData
internal typealias ScriptData = FlvScriptTagData

fun FlvTag.isScriptTag(): Boolean = this.header.tagType == FlvTagHeaderType.ScriptData && this.data is ScriptData

fun FlvTag.isTrueScripTag(): Boolean {
  if (!isScriptTag()) return false
  this.data as ScriptData
  if (data[0] !is Amf0Value.String) return false
  return (data[0] as Amf0Value.String).value == "onMetaData"
}

fun FlvTag.isVideoTag(): Boolean = this.data is VideoData && this.header.tagType == FlvTagHeaderType.Video

fun FlvTag.isAudioTag(): Boolean = this.data is AudioData && this.header.tagType == FlvTagHeaderType.Audio

fun FlvTag.isSequenceHeader(): Boolean = isVideoSequenceHeader() || isAudioSequenceHeader()

fun FlvTag.isVideoSequenceHeader(): Boolean = isVideoTag() && (this.data as VideoData).isAvcHeader()

fun FlvTag.isAudioSequenceHeader(): Boolean = isAudioTag() && (this.data as AudioData).isAacHeader()

fun FlvTag.isKeyFrame(): Boolean = isVideoTag() && (this.data as VideoData).isKeyFrame()


fun FlvData.isHeader(): Boolean = this is FlvHeader

fun FlvData.isScriptTag(): Boolean = this is FlvTag && isScriptTag()

fun FlvData.isTrueScripTag(): Boolean = this is FlvTag && isTrueScripTag()

fun FlvData.isSequenceHeader(): Boolean = this is FlvTag && isSequenceHeader()

fun FlvData.isAvcEndSequence(): Boolean {
  if (this !is FlvTag) return false
  if (!isVideoTag()) return false
  val videoData = this.data as FlvVideoTagData
  return videoData.isAvcEndOfSequence()
}

fun FlvTag.isNaluKeyFrame(): Boolean {
  if (!isVideoTag()) return false
  val videoData = this.data as FlvVideoTagData
  if (!videoData.isKeyFrame()) return false
  return videoData.isAvcNalu()
}

fun FlvTag.isNalu(): Boolean {
  if (!isVideoTag()) return false
  val videoData = this.data as FlvVideoTagData
  return videoData.isAvcNalu()
}

fun createEndOfSequenceTag(tagNum: Int, timestamp: Int, streamId: Int): FlvTag {
  val data = createEndOfSequenceData()
  return FlvTag(
    num = tagNum,
    header = createEndOfSequenceHeader(timestamp, streamId),
    data = data,
    crc32 = data.binaryData.crc32()
  )
}

internal fun createEndOfSequenceHeader(timestamp: Int, streamId: Int): FlvTagHeader =
  FlvTagHeader(tagType = FlvTagHeaderType.Video, dataSize = 5, timestamp = timestamp, streamId = streamId)


internal fun createEndOfSequenceData(): FlvTagData = FlvVideoTagData(
  frameType = FlvVideoFrameType.KEY_FRAME,
  codecId = FlvVideoCodecId.AVC,
  compositionTime = 0,
  avcPacketType = AvcPacketType.AVC_END_OF_SEQUENCE,
  binaryData = byteArrayOf()
)


internal fun createMetadataTag(tagNum: Int, timestamp: Int, streamId: Int, data: ScriptData? = null): FlvTag {
  val body = data ?: FlvScriptTagData(
    mutableListOf(
      String("onMetaData"),
      EcmaArray(mapOf("duration" to Number(0.0), "width" to Number(0.0), "height" to Number(0.0)))
    )
  )
  return FlvTag(
    num = tagNum,
    header = FlvTagHeader(
      tagType = FlvTagHeaderType.ScriptData,
      dataSize = body.bodySize,
      timestamp = timestamp,
      streamId = streamId
    ),
    data = body,
    crc32 = body.binaryData.crc32()
  )
}