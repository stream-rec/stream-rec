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

import github.hua0512.flv.FlvParser.Companion.TAG_HEADER_SIZE
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvHeaderFlags
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.amf.AmfValue
import github.hua0512.flv.data.amf.readAmf0Value
import github.hua0512.flv.data.sound.*
import github.hua0512.flv.data.tag.*
import github.hua0512.flv.data.tag.FlvTagHeaderType.*
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.FlvVideoFrameType
import github.hua0512.flv.data.video.VideoFourCC
import github.hua0512.flv.data.video.VideoPacketType
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.exceptions.FlvHeaderErrorException
import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import github.hua0512.flv.utils.CRC32Sink
import github.hua0512.flv.utils.readUI24
import github.hua0512.utils.crc32
import github.hua0512.utils.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.*
import java.nio.ByteBuffer
import kotlin.experimental.and

private const val WAIT_FOR_DATA_TIMEOUT = 30000L


/***
 * FLV parser
 * @param source Source The input source to read FLV data from.
 * @author hua0512
 * @date : 2024/6/9 10:16
 */
internal class FlvParser(private val source: Source) {


  companion object {
    internal const val TAG_HEADER_SIZE = 11
    internal const val POINTER_SIZE = 4
    internal const val AUDIO_TAG_HEADER_SIZE = 2
    internal const val VIDEO_TAG_HEADER_SIZE = 5


    private const val TAG = "FlvParser"
    internal val logger = logger(TAG)
  }

  private var tagNum = 0

  suspend fun parseHeader(): FlvHeader = withContext(Dispatchers.IO) {
    try {
      source.require(9)
    } catch (e: EOFException) {
      throw FlvHeaderErrorException("FLV header not complete")
    }

    val buffer = source.readByteArray(9)
    val crc32Sink = CRC32Sink(discardingSink())
    crc32Sink.buffered().use {
      it.write(buffer)
    }
    val signature = buffer.sliceArray(0 until 3).toString(Charsets.UTF_8)
    val version = buffer[3]
    val flags = FlvHeaderFlags(buffer[4].toInt())
    val headerSize = buffer.sliceArray(5 until 9).let { ByteBuffer.wrap(it).int }
    FlvHeader(signature, version.toInt(), flags, headerSize, crc32Sink.crc32().toLong())
  }

  fun parsePreviousTagSize(): Int = source.readInt()


  suspend fun parseTag(): FlvTag = withContext(Dispatchers.IO) {
    val header = source.parseTagHeader()

    val bodySize = when (header.tagType) {
      Audio -> header.dataSize - AUDIO_TAG_HEADER_SIZE
      Video -> header.dataSize - VIDEO_TAG_HEADER_SIZE
      ScriptData -> header.dataSize
      else -> throw FlvDataErrorException("Unsupported flv tag type: ${header.tagType}")
    }

    // require tag body size
    try {
      source.require(bodySize.toLong())
    } catch (e: EOFException) {
      // wait for more data
      throw EOFException("No flv tag data available")
    }

    when (header.tagType) {
      Audio -> parseAudioTagData(bodySize).let {
        FlvTag(++tagNum, header, it.first, it.second)
      }

      Video -> parseVideoTagData(bodySize).let {
        FlvTag(++tagNum, header, it.first, it.second)
      }

      ScriptData -> source.parseScriptTagData(bodySize).let { data ->
//        if (header.dataSize != data.first.size) {
//          logger.warn("Script tag size mismatch: header=${header.dataSize}, body=${data.first.size}")
//        }
        // update data size to actual body size
        // this is to avoid the case where the script tag size is larger than the actual body size
        // probable due to incorrect size calculation, or missing data in parsing, etc.
        FlvTag(++tagNum, header.copy(dataSize = data.first.size), data.first, data.second)
      }

      else -> throw FlvDataErrorException("Unsupported flv tag type: ${header.tagType}")
    }
  }


  private fun parseAudioTagData(bodySize: Int): Pair<FlvAudioTagData, Long> {
    try {
      source.require(bodySize.toLong())
    } catch (e: EOFException) {
      throw FlvDataErrorException("FLV audio tag data not complete")
    }

    val flag = source.readUByte().toInt()
    val soundFormat = FlvSoundFormat.from(flag ushr 4)

    val soundRate: FlvSoundRate
    val soundSize: FlvSoundSize
    val soundType: FlvSoundType
    val audioFourCC: AudioFourCC?

    if (soundFormat == FlvSoundFormat.EX_HEADER) {
      // ExHeader format
      // Format specific config byte
      val formatConfig = source.readUByte().toInt()

      // Get the audio format from the format config
      val format = AudioFourCC.from(formatConfig)
      audioFourCC = format

      when (format) {
        AudioFourCC.AAC -> {
          soundRate = FlvSoundRate.from((formatConfig ushr 2) and 0x03)
          soundSize = FlvSoundSize.from((formatConfig ushr 1) and 0x01)
          soundType = FlvSoundType.from(formatConfig and 0x01)
        }

        AudioFourCC.OPUS -> {
          // For Opus, we'll use the rate from the format config
          // even though Opus internally operates at 48kHz
          soundRate = FlvSoundRate.from((formatConfig ushr 2) and 0x03)
          soundSize = FlvSoundSize.SOUND_16_BIT  // Opus uses fixed 16-bit samples
          soundType = FlvSoundType.STEREO        // Opus uses fixed stereo
        }

        AudioFourCC.FLAC -> {
          soundRate = FlvSoundRate.from((formatConfig ushr 2) and 0x03)
          soundSize = FlvSoundSize.from((formatConfig ushr 1) and 0x01)
          soundType = FlvSoundType.from(formatConfig and 0x01)
        }

        AudioFourCC.MP3 -> {
          soundRate = FlvSoundRate.from((formatConfig ushr 2) and 0x03)
          soundSize = FlvSoundSize.from((formatConfig ushr 1) and 0x01)
          soundType = FlvSoundType.from(formatConfig and 0x01)
        }
      }
    } else {
      // legacy sound format
      soundRate = FlvSoundRate.from((flag ushr 2) and 0b0000_0011)
      soundSize = FlvSoundSize.from((flag ushr 1) and 0b0000_0001)
      soundType = FlvSoundType.from(flag and 0b0000_0001)
      audioFourCC = null
    }

    // AAC packet type, 1 bit
    val aacPacketType = if (soundFormat == FlvSoundFormat.AAC ||
      (soundFormat == FlvSoundFormat.EX_HEADER && audioFourCC == AudioFourCC.AAC)
    ) {
      source.readUByte().toInt().let { AACPacketType.from(it) }
    } else {
      null
    }

    // Read body data
    val body = source.readByteArray(bodySize)

    // Calculate CRC32
    val crc32Value = calculateCrc32(body)

    return FlvAudioTagData(
      format = soundFormat,
      rate = soundRate,
      soundSize = soundSize,
      type = soundType,
      fourCC = audioFourCC,
      packetType = aacPacketType,
      binaryData = body
    ) to crc32Value
  }

  private fun parseVideoTagData(bodySize: Int): Pair<FlvVideoTagData, Long> {
    val flag = source.readUByte().toInt()
    val frameTypeValue = flag ushr 4
    val frameType = FlvVideoFrameType.from(frameTypeValue)
      ?: throw FlvDataErrorException("Unsupported flv video frame type: $frameTypeValue")

    val codecIdValue = flag and 0b0000_1111
    val codecId = FlvVideoCodecId.from(codecIdValue)

    val videoFourCC: VideoFourCC?
    val compositionTime: Int
    val packetType: VideoPacketType?
    var isExHeader = false
    var remainingBodySize = bodySize

    when (codecId) {
      FlvVideoCodecId.AVC -> {
        packetType = source.readUByte().toInt().let { VideoPacketType.from(it) }
        compositionTime = source.readUI24().toInt()
        videoFourCC = VideoFourCC.AVC1
      }

      FlvVideoCodecId.HEVC -> {
        packetType = source.readUByte().toInt().let { VideoPacketType.from(it) }
        compositionTime = source.readUI24().toInt()
        videoFourCC = VideoFourCC.HVC1
      }

      FlvVideoCodecId.EX_HEADER -> {
        logger.debug("ExHeader video tag detected")
        isExHeader = true
        // Read FourCC (4 bytes)
        val fourCCValue = source.readInt()
        videoFourCC = VideoFourCC.from(fourCCValue)
        remainingBodySize -= 4  // Subtract FourCC bytes

        // Read packet type and composition time
        packetType = source.readUByte().toInt().let { VideoPacketType.from(it) }
        compositionTime = source.readUI24().toInt()
      }

      else -> throw FlvDataErrorException("Unsupported video codec: $codecId")
    }

    // Read remaining body data
    val body = source.readByteArray(remainingBodySize)

    // Calculate CRC32
    val crc32Value = calculateCrc32(body)

    return FlvVideoTagData(
      frameType = frameType,
      codecId = codecId,
      compositionTime = compositionTime,
      packetType = packetType,
      fourCC = videoFourCC,
      isExHeader = isExHeader,
      binaryData = body
    ) to crc32Value
  }
}

// Helper function to calculate CRC32
private fun calculateCrc32(data: ByteArray): Long = data.crc32()


/**
 * Parse FLV tag header
 * @receiver Source The input source to read FLV data from.
 * @return FlvTagHeader The parsed FLV tag header.
 * @throws FlvTagHeaderErrorException If the FLV tag header is not complete.
 */

internal fun Source.parseTagHeader(): FlvTagHeader {
  // require tag header size
  try {
    require(TAG_HEADER_SIZE.toLong())
  } catch (e: EOFException) {
    throw EOFException("Waited for more data, but no flv tag header available")
  }

  // flag is 1 byte
  val flag = readByte()
  val filtered = flag and 0b0010_0000
  if (filtered != 0.toByte()) {
    throw FlvDataErrorException("Filtered flv tag detected.")
  }

  val tagType = FlvTagHeaderType.from(flag and 0b0001_1111)
  val dataSize = readUI24().toInt()
  // 3 bytes timestamp
  val timestamp = readUI24().toInt()
  // 1 byte timestamp extended
  val timestampExtended = readUByte().toInt()
  // final timestamp, 3 bytes timestamp (lower bits) + 1 byte timestamp extended (higher 8 bits)
  val finalTimestamp = (timestampExtended shl 24) or timestamp
  // 3 bytes stream id
  val streamId = readUI24().toInt()
  return FlvTagHeader(
    tagType,
    dataSize = dataSize,
    timestamp = finalTimestamp,
    streamId = streamId
  )
}


internal fun Source.parseScriptTagData(bodySize: Int): Pair<FlvScriptTagData, Long> {
  val buffer = Buffer()
  readTo(buffer, bodySize.toLong())

  try {
    buffer.require(2)
  } catch (e: EOFException) {
    throw FlvDataErrorException("FLV script tag data not complete")
  }

  // Create a copy of the data for CRC calculation
  val body = buffer.copy().use {
    it.readByteArray()
  }

  val amfValues = mutableListOf<AmfValue>()
  // Read AMF values from the original buffer
  buffer.use {
    while (!it.exhausted()) {
      amfValues.add(readAmf0Value(it))
    }
  }

  return FlvScriptTagData(amfValues) to calculateCrc32(body)
}