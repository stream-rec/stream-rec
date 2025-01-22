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
import github.hua0512.flv.FlvParser.Companion.logger
import github.hua0512.flv.data.FlvData
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
import github.hua0512.flv.utils.readUI24
import github.hua0512.utils.crc32
import github.hua0512.utils.logger
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.*
import kotlinx.io.Buffer
import java.nio.ByteBuffer
import kotlin.experimental.and


internal typealias onTagRead = suspend (FlvData) -> Unit


// Sealed class for validation results
private sealed class TagValidation {
  data class Valid(val dataSize: Int) : TagValidation()
  data class Skip(val skipBytes: Int) : TagValidation()
  data object Invalid : TagValidation()
}


/***
 * FLV parser
 * @param source Source The input source to read FLV data from.
 * @author hua0512
 * @date : 2024/6/9 10:16
 */
internal class FlvParser(private val source: Source) : AutoCloseable {

  companion object {
    internal const val TAG_HEADER_SIZE = 11
    internal const val POINTER_SIZE = 4
    internal const val AUDIO_TAG_HEADER_SIZE = 2
    internal const val VIDEO_TAG_HEADER_SIZE = 5

    private const val TAG = "FlvParser"
    internal val logger = logger(TAG)


    private const val STREAM_RECOVERY_BUFFER_SIZE = 1024 * 1024 * 2 // 2MB buffer for recovery
    private const val MAX_RECOVERY_ATTEMPTS = 5
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 0L

    // Validation constants
    private const val MAX_TAG_SIZE = 1024 * 1024 * 5  // 5MB max tag size (for 4K video)
    private const val MIN_TAG_SIZE = 1  // Minimum tag size
    private const val MAX_TIMESTAMP_JUMP = 5000  // 5 seconds max jump
    private const val MAX_CONSECUTIVE_INVALID = 1000  // Max bytes to skip before giving up
  }

  private var tagNum = 0
  private lateinit var header: FlvHeader
  private var lastValidTimestamp: Int = 0 // Track last valid timestamp for recovery

  /**
   * Read FLV header and validate it
   * @param onTagRead Callback for when a tag is read
   * @throws FlvHeaderErrorException If the header is invalid
   * @throws FlvDataErrorException If the first previous tag size is not 0
   */
  suspend fun readHeader(onTagRead: onTagRead) {
    if (::header.isInitialized) {
      logger.error("Header already read")
      throw FlvDataErrorException("Header already read")
    }
    header = parseHeader()
    val previousTagSize = parsePreviousTagSize()
    if (previousTagSize != 0) {
      logger.error("First previous tag size must be 0, but got $previousTagSize")
      throw FlvDataErrorException("First previous tag size must be 0, but got $previousTagSize")
    }
    onTagRead(header)
  }

  /**
   * Read a single FLV tag
   * @return The parsed FLV tag
   */
  suspend fun readTag(): FlvTag {
    var retryCount = 0
    var lastException: Exception? = null

    while (retryCount < MAX_RETRY_ATTEMPTS) {
      try {
        val tag = parseTag()
        val previousTagSize = parsePreviousTagSize()

        if (previousTagSize != tag.size.toInt()) {
          if (tag.data is FlvScriptTagData) {
            return tag
          }
          throw FlvDataErrorException("Previous tag size mismatch: expected ${tag.size}, got $previousTagSize")
        }

        // Update last valid timestamp for recovery
        lastValidTimestamp = tag.header.timestamp
        return tag

      } catch (e: EOFException) {
        // Wait for more data
        lastException = e
        delay(RETRY_DELAY_MS)
        retryCount++

      } catch (e: Exception) {
        logger.error("Error reading tag (attempt ${retryCount + 1}): ${e.message}")
        lastException = e

        if (e is CancellationException) {
          throw e
        }

        // Attempt stream recovery
        if (attemptStreamRecovery()) {
          logger.info("Stream recovered, continuing...")
          continue
        }

        retryCount++
        if (retryCount < MAX_RETRY_ATTEMPTS) {
          delay(RETRY_DELAY_MS)
        }
      }
    }

    throw FlvDataErrorException("Failed to read tag after $MAX_RETRY_ATTEMPTS attempts: ${lastException?.message}")
  }

  /**
   * Read FLV tags continuously
   * @param disableLogging Whether to disable logging
   * @param onTagRead Callback for when a tag is read
   */
  suspend inline fun readTags(disableLogging: Boolean = false, onTagRead: onTagRead) {
    var consecutiveErrors = 0

    while (true) {
      try {
        onTagRead(readTag())
        consecutiveErrors = 0 // Reset error counter on success
      } catch (e: Exception) {
        consecutiveErrors++

        if (!disableLogging) {
          logger.error("Read tag error (consecutive errors: $consecutiveErrors): ${e.message}")
        }

        if (consecutiveErrors >= MAX_RETRY_ATTEMPTS) {
          throw e
        }

        // Add exponential backoff
        delay(RETRY_DELAY_MS * (1 shl (consecutiveErrors - 1)))
      }
    }
  }

  /**
   * Read all FLV data
   * @param disableLogging Whether to disable logging
   * @param onTagRead Callback for when a tag is read
   */
  suspend fun readAll(disableLogging: Boolean = false, onTagRead: onTagRead) {
    if (!::header.isInitialized) {
      readHeader(onTagRead)
    }
    readTags(disableLogging, onTagRead)
  }

  suspend fun parseHeader(): FlvHeader = withContext(Dispatchers.IO) {
    try {
      source.require(9)
    } catch (e: EOFException) {
      throw FlvHeaderErrorException("FLV header not complete")
    }

    val buffer = source.readByteArray(9)
    val signature = buffer.sliceArray(0 until 3).toString(Charsets.UTF_8)
    val version = buffer[3]
    val flags = FlvHeaderFlags(buffer[4].toInt())
    val headerSize = buffer.sliceArray(5 until 9).let { ByteBuffer.wrap(it).int }
    FlvHeader(signature, version.toInt(), flags, headerSize, calculateCrc32(buffer))
  }

  private fun parsePreviousTagSize(): Int = source.readInt()

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

  override fun close() {
    source.close()
  }

  // Add method to attempt stream recovery
  private suspend fun attemptStreamRecovery(): Boolean {
    logger.warn("Attempting stream recovery...")
    var recoveryBuffer = ByteArray(STREAM_RECOVERY_BUFFER_SIZE)
    var attempts = 0
    var consecutiveValidTags = 0
    val requiredValidTags = 2 // Require finding multiple valid tags in sequence

    while (attempts++ < MAX_RECOVERY_ATTEMPTS) {
      try {
        // Read available data with overlap from previous attempt
        val bytesRead = try {
          source.require(STREAM_RECOVERY_BUFFER_SIZE.toLong())
          recoveryBuffer = source.readByteArray(STREAM_RECOVERY_BUFFER_SIZE)
          STREAM_RECOVERY_BUFFER_SIZE
        } catch (e: EOFException) {
          if (!source.exhausted()) {
            val available = source.remaining.toInt()
            if (available > 0) {
              recoveryBuffer = source.readByteArray(available)
              available
            } else return false
          } else return false
        }

        // Search for sequence of valid FLV tags
        var currentOffset = 0
        while (currentOffset < bytesRead - TAG_HEADER_SIZE) {
          val tagInfo = findNextValidTag(recoveryBuffer, currentOffset, bytesRead)
          if (tagInfo == null) {
            // No valid tag found, move to next chunk
            break
          }

          val (offset, dataSize) = tagInfo

          // Verify we can read the full tag including its data
          if (offset + TAG_HEADER_SIZE + dataSize + 4 <= bytesRead) { // +4 for previous tag size
            // Verify previous tag size field matches
            val expectedPrevTagSize = dataSize + TAG_HEADER_SIZE
            val actualPrevTagSize = getPreviousTagSize(recoveryBuffer, offset + TAG_HEADER_SIZE + dataSize)

            if (actualPrevTagSize == expectedPrevTagSize) {
              consecutiveValidTags++
              if (consecutiveValidTags >= requiredValidTags) {
                // Found required number of valid tags in sequence
                if (offset > 0) {
                  source.skip(offset.toLong())
                }
                logger.info("Stream recovered at position $offset after finding $consecutiveValidTags valid tags")
                return true
              }
              // Move to next potential tag
              currentOffset = offset + TAG_HEADER_SIZE + dataSize + 4
              continue
            }
          }

          // If validation failed, try from next byte
          consecutiveValidTags = 0
          currentOffset = offset + 1
        }

        // Keep last portion for overlap with next chunk
        val keepBytes = minOf(TAG_HEADER_SIZE * 2, bytesRead)
        source.skip((bytesRead - keepBytes).toLong())
        delay(RETRY_DELAY_MS)

      } catch (e: Exception) {
        logger.error("Recovery attempt $attempts failed: ${e.message}")
        delay(RETRY_DELAY_MS * (1 shl (attempts - 1))) // Exponential backoff
      }
    }
    return false
  }

  private fun findNextValidTag(buffer: ByteArray, startOffset: Int, maxLength: Int): Pair<Int, Int>? {
    var i = startOffset
    var invalidCount = 0

    while (i < maxLength - TAG_HEADER_SIZE && invalidCount < MAX_CONSECUTIVE_INVALID) {
      when (buffer[i].toInt() and 0x1F) {
        0x08, 0x09, 0x12 -> {
          val validationResult = validateTagSequence(buffer, i, maxLength)
          when (validationResult) {
            is TagValidation.Valid -> {
              invalidCount = 0
              return i to validationResult.dataSize
            }

            is TagValidation.Skip -> {
              i += validationResult.skipBytes
              invalidCount = 0
            }

            TagValidation.Invalid -> {
              i++
              invalidCount++
            }
          }
        }

        else -> {
          i++
          invalidCount++
        }
      }
    }

    if (invalidCount >= MAX_CONSECUTIVE_INVALID) {
      logger.warn("Too many invalid bytes encountered, giving up recovery at offset $i")
    }

    return null
  }


  private fun validateTagSequence(buffer: ByteArray, offset: Int, maxLength: Int): TagValidation {
    if (offset + TAG_HEADER_SIZE > maxLength) {
      logger.debug("Skip: Insufficient data for tag header at offset $offset")
      return TagValidation.Invalid
    }

    // Get data size early for validation
    val dataSize = ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
            (buffer[offset + 3].toInt() and 0xFF)

    // Stricter size validation
    if (dataSize < MIN_TAG_SIZE || dataSize > MAX_TAG_SIZE) {
      logger.debug("Skip: Invalid data size $dataSize at offset $offset (must be between $MIN_TAG_SIZE and $MAX_TAG_SIZE)")
      return TagValidation.Invalid
    }

    // Check if we can validate the full tag including its data
    val fullTagSize = TAG_HEADER_SIZE + dataSize + 4 // header + data + previous tag size
    if (offset + fullTagSize > maxLength) {
      // Instead of waiting for more data, treat as invalid if size is suspicious
      if (dataSize > MAX_TAG_SIZE / 2) {
        logger.debug("Skip: Suspiciously large tag size $dataSize at offset $offset")
        return TagValidation.Invalid
      }
      return TagValidation.Skip(maxLength - offset - TAG_HEADER_SIZE)
    }

    // Validate tag type first
    val tagType = buffer[offset].toInt() and 0x1F
    if (tagType !in arrayOf(0x08, 0x09, 0x12)) {
      logger.debug("Skip: Invalid tag type $tagType at offset $offset")
      return TagValidation.Invalid
    }

    // Validate timestamp with more flexible rules for live streams
    val timestamp = ((buffer[offset + 7].toInt() and 0xFF) shl 24) or
            ((buffer[offset + 4].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 5].toInt() and 0xFF) shl 8) or
            (buffer[offset + 6].toInt() and 0xFF)

    // TODO: Skip this check for now, further investigation is needed
    // if (lastValidTimestamp > 0) {
    //   val timeDiff = abs(timestamp - lastValidTimestamp)
    //   if (timeDiff > MAX_TIMESTAMP_JUMP && timestamp != 0) {
    //     logger.debug("Skip: Timestamp jump too large ($timeDiff ms) at offset $offset")
    //     return TagValidation.Invalid
    //   }
    // }

    // Type-specific validation with size ranges
    when (tagType) {
      0x08 -> { // Audio
        if (dataSize < AUDIO_TAG_HEADER_SIZE || dataSize > 10000) { // Audio tags are usually small
          logger.debug("Skip: Invalid audio tag size $dataSize at offset $offset")
          return TagValidation.Invalid
        }
        val audioFormat = (buffer[offset + TAG_HEADER_SIZE].toInt() ushr 4) and 0x0F
        if (audioFormat > 15) return TagValidation.Invalid
      }

      0x09 -> { // Video
        if (dataSize < VIDEO_TAG_HEADER_SIZE || dataSize > MAX_TAG_SIZE) {
          logger.debug("Skip: Invalid video tag size $dataSize at offset $offset")
          return TagValidation.Invalid
        }
        val frameType = (buffer[offset + TAG_HEADER_SIZE].toInt() ushr 4) and 0x0F
        val codecId = buffer[offset + TAG_HEADER_SIZE].toInt() and 0x0F
        if (frameType > 5 || codecId > 7) return TagValidation.Invalid
      }

      0x12 -> { // Script
        if (dataSize < 3 || dataSize > 50000) { // Script tags are usually small
          logger.debug("Skip: Invalid script tag size $dataSize at offset $offset")
          return TagValidation.Invalid
        }
      }
    }

    // Validate previous tag size
    val expectedPrevTagSize = dataSize + TAG_HEADER_SIZE
    val actualPrevTagSize = getPreviousTagSize(buffer, offset + TAG_HEADER_SIZE + dataSize)

    if (actualPrevTagSize == expectedPrevTagSize || tagType == 0x12) {
      logger.debug(
        "Valid tag found: type=${
          when (tagType) {
            0x08 -> "Audio"
            0x09 -> "Video"
            0x12 -> "Script"
            else -> "Unknown"
          }
        }, size=$dataSize, timestamp=$timestamp at offset $offset"
      )
      return TagValidation.Valid(dataSize)
    }

    return TagValidation.Invalid
  }

  private fun getPreviousTagSize(buffer: ByteArray, offset: Int): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 24) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
            (buffer[offset + 3].toInt() and 0xFF)
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
  try {
    require(TAG_HEADER_SIZE.toLong())
  } catch (e: EOFException) {
    throw EOFException("Waited for more data, but no flv tag header available")
  }

  // Read flag byte
  val flag = readByte()
  val filtered = (flag.toInt() and 0b0010_0000) != 0
  val tagType = FlvTagHeaderType.from(flag and 0b0001_1111)

  var dataSize = readUI24().toInt()
  val timestamp = readUI24().toInt()
  val timestampExtended = readUByte().toInt()
  val finalTimestamp = (timestampExtended shl 24) or timestamp
  val streamId = readUI24().toInt()

  // Handle filtered tag if present
  var filteredInfo: FlvFilteredTagInfo? = null
  if (filtered) {
    try {
      // Read filter header
      require(1)
      val filterType = readUByte().toInt()

      // Read filter params based on filter type
      val params = when (filterType) {
        0x01 -> { // Encryption filter
          require(1)
          val paramSize = readUByte().toInt()
          require(paramSize.toLong())
          readByteArray(paramSize).also {
            dataSize -= (1 + 1 + paramSize) // Subtract filter metadata size
          }
        }

        0x02 -> { // Compression filter
          require(1)
          val paramSize = readUByte().toInt()
          require(paramSize.toLong())
          readByteArray(paramSize).also {
            dataSize -= (1 + 1 + paramSize) // Subtract filter metadata size
          }
        }

        else -> {
          logger.warn("Unknown filter type: $filterType")
          dataSize -= 1 // Subtract only filter type byte
          ByteArray(0)
        }
      }

      filteredInfo = FlvFilteredTagInfo(filterType, params)
    } catch (e: EOFException) {
      logger.warn("Incomplete filter info in tag header")
      // Continue without filter info if we can't read it
    }
  }

  return FlvTagHeader(
    tagType = tagType,
    dataSize = dataSize,
    timestamp = finalTimestamp,
    streamId = streamId,
    filteredInfo = filteredInfo
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
  // Read AMF values from buffer
  buffer.use {
    while (!it.exhausted()) {
      amfValues.add(readAmf0Value(it))
    }
  }

  return FlvScriptTagData(amfValues) to calculateCrc32(body)
}