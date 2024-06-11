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

import github.hua0512.flv.FlvParser.Companion.TAG_HEADER_SIZE
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvHeaderFlags
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.amf.AmfValue
import github.hua0512.flv.data.amf.readAmf0Value
import github.hua0512.flv.data.amf.readAmf3Value
import github.hua0512.flv.data.avc.AvcPacketType
import github.hua0512.flv.data.sound.AACPacketType
import github.hua0512.flv.data.sound.FlvSoundFormat
import github.hua0512.flv.data.sound.FlvSoundRate
import github.hua0512.flv.data.sound.FlvSoundSize
import github.hua0512.flv.data.sound.FlvSoundType
import github.hua0512.flv.data.tag.FlvAudioTagData
import github.hua0512.flv.data.tag.FlvScriptTagData
import github.hua0512.flv.data.tag.FlvTagHeader
import github.hua0512.flv.data.tag.FlvTagHeaderType
import github.hua0512.flv.data.tag.FlvVideoTagData
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.FlvVideoFrameType
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.exceptions.FlvHeaderErrorException
import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import github.hua0512.flv.utils.crc32
import github.hua0512.flv.utils.logger
import github.hua0512.flv.utils.readUI24
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

/***
 * FLV parser
 * @param ins DataInputStream input stream
 * @author hua0512
 * @date : 2024/6/9 10:16
 */
internal class FlvParser(private val ins: DataInputStream) {


  companion object {
    internal const val TAG_HEADER_SIZE = 11
    internal const val POINTER_SIZE = 4
    internal const val AUDIO_TAG_HEADER_SIZE = 2
    internal const val VIDEO_TAG_HEADER_SIZE = 5

    private const val TAG = "FlvParser"
    internal val logger = logger(TAG)
  }

  var backupTimestamp = false
  var restoreTimestamp = false

  private var tagNum = 0

  suspend fun parseHeader(): FlvHeader = withContext(Dispatchers.IO) {
    val buffer = ins.readNBytes(9)

    if (buffer.size < 9) {
      throw FlvHeaderErrorException("FLV header not complete, buffer size: ${buffer.size}")
    }
    val signature = buffer.sliceArray(0 until 3).toString(Charsets.UTF_8)
    val version = buffer[3]
    val flags = FlvHeaderFlags(buffer[4].toInt())
    val headerSize = buffer.sliceArray(5 until 9).let { ByteBuffer.wrap(it).int }
    FlvHeader(signature, version.toUInt(), flags, headerSize.toUInt(), buffer.crc32())
  }

  fun parsePreviousTagSize(): Int = ins.readInt()

  fun seekToPreviousTag() {
    ins.reset()
  }


  suspend fun parseTag(): FlvTag = withContext(Dispatchers.IO) {
    ins.mark(Integer.MAX_VALUE)

    val header = ins.parseTagHeader()

    when (header.tagType) {
      FlvTagHeaderType.Audio -> parseAudioTagData(header.dataSize.toInt() - AUDIO_TAG_HEADER_SIZE).let {
        FlvTag(++tagNum, header, it, it.binaryData.crc32())
      }

      FlvTagHeaderType.Video -> parseVideoTagData(header.dataSize.toInt() - VIDEO_TAG_HEADER_SIZE).let {
        FlvTag(++tagNum, header, it, it.binaryData.crc32())
      }

      FlvTagHeaderType.ScriptData -> ins.parseScriptTagData(header.dataSize.toInt()).let { data ->
        FlvTag(++tagNum, header, data.first, data.second)
      }

      else -> throw FlvDataErrorException("Unsupported flv tag type: ${header.tagType}")
    }
  }


  private fun parseAudioTagData(bodySize: Int): FlvAudioTagData {
    val flag = ins.readUnsignedByte()
    val soundFormat = FlvSoundFormat.from(flag ushr 4)
    if (soundFormat != FlvSoundFormat.AAC) {
      throw FlvDataErrorException("Unsupported flv sound format: $soundFormat")
    }
    val soundRate = FlvSoundRate.from((flag ushr 2) and 0b0000_0011)
    val soundSize = FlvSoundSize.from((flag ushr 1) and 0b0000_0001)
    val soundType = FlvSoundType.from(flag and 0b0000_0001)
    // AAC packet type, 1 bit
    val aacPacketType = ins.readUnsignedByte().let { AACPacketType.from(it) }
    // read body
    val body = ins.readNBytes(bodySize)
    return FlvAudioTagData(
      format = soundFormat,
      rate = soundRate,
      size = soundSize,
      type = soundType,
      packetType = aacPacketType,
      binaryData = body
    )
  }

  private fun parseVideoTagData(bodySize: Int): FlvVideoTagData {
    val flag = ins.readUnsignedByte()
    val frameTypeValue = flag ushr 4
    val frameType = FlvVideoFrameType.from(frameTypeValue) ?: throw FlvDataErrorException("Unsupported flv video frame type: $frameTypeValue")
    val codecId = flag and 0b0000_1111
    val codec = FlvVideoCodecId.from(codecId) ?: throw FlvDataErrorException("Unsupported flv video codec id: $codecId")
    // TODO : SUPPORT CHINESE HEVC
    if (codec != FlvVideoCodecId.AVC) {
      throw FlvDataErrorException("Unsupported flv video codec: $codec")
    }
    val avcPacketType = ins.readUnsignedByte().let { AvcPacketType.from(it) }
    val compositionTime = ins.readUI24()
    val data = ins.readNBytes(bodySize)
    return FlvVideoTagData(
      frameType = frameType,
      codecId = codec,
      compositionTime = compositionTime,
      avcPacketType = avcPacketType,
      binaryData = data
    )
  }

}


internal fun InputStream.parseTagHeader(): FlvTagHeader {

  fun ByteBuffer.read3BytesAsInt(): Int {
    // Read 3 bytes from the buffer
    val byte1 = get().toInt() and 0xFF
    val byte2 = get().toInt() and 0xFF
    val byte3 = get().toInt() and 0xFF

    // Combine the 3 bytes into an Int
    return (byte1 shl 16) or (byte2 shl 8) or byte3
  }

  // read tag header
  val read = readNBytes(TAG_HEADER_SIZE)
  // If the buffer size is less than 11, it means the tag header is not complete
  if (read.size < TAG_HEADER_SIZE) {
    FlvParser.logger.debug("flv tag header not complete, buffer size: ${read.size}, available: ${available()}")
    throw FlvTagHeaderErrorException("FLV tag header not complete, buffer size: ${read.size}")
  }

  val buffer = ByteBuffer.wrap(read).apply {
    order(ByteOrder.BIG_ENDIAN)
  }

  // flag is 1 byte
  val flag = buffer.get()
  val filtered = flag and 0b0010_0000
  if (filtered != 0.toByte()) {
    throw FlvDataErrorException("Filtered flv tag detected.")
  }

  val tagType = FlvTagHeaderType.from(flag and 0b0001_1111)
  val dataSize = buffer.read3BytesAsInt().toUInt()
  // 3 bytes timestamp
  val timestamp = buffer.read3BytesAsInt().toUInt()
  // 1 byte timestamp extended
  val timestampExtended = buffer.get().toUInt()
  // final timestamp, 3 bytes timestamp (lower bits) + 1 byte timestamp extended (higher 8 bits)
  val finalTimestamp = (timestampExtended shl 24 or timestamp).toLong()
  // 3 bytes stream id
  val streamId = buffer.read3BytesAsInt().toUInt()
  return FlvTagHeader(
    tagType,
    dataSize = dataSize,
    timestamp = finalTimestamp,
    streamId = streamId
  )
}


internal fun InputStream.parseScriptTagData(bodySize: Int): Pair<FlvScriptTagData, Long> {

  fun identifyMetadataType(input: DataInputStream): String {
    input.mark(1)
    val firstByte = input.readByte().toInt()
    input.reset()
    return if (firstByte == 0x11) "AMF3" else "AMF0"
  }

  val body = readNBytes(bodySize)
  val crc32 = body.crc32()
  val dataInputStream = DataInputStream(ByteArrayInputStream(body))
  val amfValues = mutableListOf<AmfValue>()

  dataInputStream.use {
    val metadataType = identifyMetadataType(dataInputStream)
    when (metadataType) {
      "AMF0" -> {
        while (dataInputStream.available() > 0)
          readAmf0Value(dataInputStream).also {
            amfValues.add(it)
          }
      }

      "AMF3" -> {
        while (dataInputStream.available() > 0)
          readAmf3Value(dataInputStream).also {
            amfValues.add(it)
          }
      }
    }
  }

  return FlvScriptTagData(amfValues) to crc32
}