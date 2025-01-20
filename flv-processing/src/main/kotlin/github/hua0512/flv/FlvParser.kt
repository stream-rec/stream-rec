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
import github.hua0512.flv.data.avc.AvcPacketType
import github.hua0512.flv.data.sound.*
import github.hua0512.flv.data.tag.*
import github.hua0512.flv.data.tag.FlvTagHeaderType.*
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.data.video.FlvVideoFrameType
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.exceptions.FlvHeaderErrorException
import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import github.hua0512.flv.utils.CRC32Sink
import github.hua0512.flv.utils.readUI24
import github.hua0512.utils.crc32
import github.hua0512.utils.logger
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.*
import kotlinx.io.Buffer
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
      var hasData = false
      withTimeoutOrNull(WAIT_FOR_DATA_TIMEOUT) {
        while (true) {
          if (source.exhausted() || source.remaining < bodySize) {
            logger.debug("Waiting for more data, remaining=${source.remaining}, required=$bodySize")
            delay(1000)
            continue
          } else {
            hasData = true
            break
          }
        }
      }
      if (!hasData) {
        throw EOFException("Waited for more data, but no flv tag data available")
      }
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
    if (soundFormat != FlvSoundFormat.AAC) {
      throw FlvDataErrorException("Unsupported flv sound format: $soundFormat")
    }
    val soundRate = FlvSoundRate.from((flag ushr 2) and 0b0000_0011)
    val soundSize = FlvSoundSize.from((flag ushr 1) and 0b0000_0001)
    val soundType = FlvSoundType.from(flag and 0b0000_0001)
    // AAC packet type, 1 bit
    val aacPacketType = source.readUByte().toInt().let { AACPacketType.from(it) }

    // Read body data
    val body = source.readByteArray(bodySize)

    // Calculate CRC32
    val crc32Value = calculateCrc32(body)

    return FlvAudioTagData(
      format = soundFormat,
      rate = soundRate,
      soundSize = soundSize,
      type = soundType,
      packetType = aacPacketType,
      binaryData = body
    ) to crc32Value
  }

  private fun parseVideoTagData(bodySize: Int): Pair<FlvVideoTagData, Long> {
    val flag = source.readUByte().toInt()
    val frameTypeValue = flag ushr 4
    val frameType = FlvVideoFrameType.from(frameTypeValue)
      ?: throw FlvDataErrorException("Unsupported flv video frame type: $frameTypeValue")
    val codecId = flag and 0b0000_1111
    val codec = FlvVideoCodecId.from(codecId).takeIf { it == FlvVideoCodecId.AVC }
      ?: throw FlvDataErrorException("Unsupported flv video codec id: $codecId")
    val avcPacketType = source.readUByte().toInt().let { AvcPacketType.from(it) }
    val compositionTime = source.readUI24().toInt()

    // Read body data
    val body = source.readByteArray(bodySize)

    // Calculate CRC32
    val crc32Value = calculateCrc32(body)

    return FlvVideoTagData(
      frameType = frameType,
      codecId = codec,
      compositionTime = compositionTime,
      avcPacketType = avcPacketType,
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

internal suspend fun Source.parseTagHeader(): FlvTagHeader {
  // require tag header size
  try {
    require(TAG_HEADER_SIZE.toLong())
  } catch (e: EOFException) {
    // wait for more data
    var hasData = false
    withTimeoutOrNull(WAIT_FOR_DATA_TIMEOUT) {
      while (true) {
        if (this@parseTagHeader.exhausted() || this@parseTagHeader.remaining < TAG_HEADER_SIZE) {
          FlvParser.logger.debug("Waiting for more data, remaining=${this@parseTagHeader.remaining}, required=$TAG_HEADER_SIZE")
          delay(1000)
          continue
        } else {
          hasData = true
          break
        }
      }
    }
    // throw exception if tag header is not complete
    if (!hasData) {
      throw EOFException("Waited for more data, but no flv tag header available")
    }
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