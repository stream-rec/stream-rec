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
import github.hua0512.flv.data.other.FlvKeyframe
import github.hua0512.flv.data.other.FlvMetadataInfo
import github.hua0512.flv.data.video.VideoResolution
import github.hua0512.flv.utils.*
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import io.exoquery.kmp.pprint


/**
 * FLV analyzer class responsible for parsing FLV headers and tags to extract metadata and statistics.
 * It accumulates information about audio, video, keyframes, duration, sizes, and data rates.
 *
 * @property context The streamer context, potentially used for logging or other contextual information.
 * @author hua0512
 * @date : 2024/9/8 21:18
 */
class FlvAnalyzer(val context: StreamerContext) {

  companion object {
    private const val TAG = "FlvAnalyzer"
    private val logger = logger(TAG)

    // Default resolution constant
    private val DEFAULT_RESOLUTION = VideoResolution(0, 0)
  }

  // --- State Variables ---
  private var numTags = 0
  private var numAudioTags = 0
  private var numVideoTags = 0
  private var numFrameTags = 0
  private var metadataCount = 0

  private var tagsSize: Long = 0L
  private var dataSize: Long = 0L
  private var audioTagsSize: Long = 0L
  private var audioDataSize: Long = 0L
  private var videoTagsSize: Long = 0L
  private var videoDataSize: Long = 0L
  var fileSize: Long = 0L
    private set

  private var lastTimestamp: Int = 0
  private var lastAudioTimestamp: Int = 0
  private var lastVideoTimestamp: Int = 0
  private var lastKeyframeTimestamp: Int = 0

  private var lastKeyframeFilePosition: Long = 0L

  private var resolution: VideoResolution? = null
  private var keyframesMap = mutableMapOf<Long, Long>() // Timestamp (ms) -> File Position (bytes)

  private var hasAudio = false
  private var audioInfo: AudioData? = null
  private var hasVideo = false
  private var videoInfo: VideoData? = null

  private var headerSize = 0

  val frameRate: Float
    get() = if (lastVideoTimestamp > 0) {
      (numFrameTags.toFloat() * 1000) / lastVideoTimestamp
    } else {
      0.0f
    }

  val audioDataRate: Float // bps
    get() = if (lastAudioTimestamp > 0) {
      (audioDataSize * 8.0f) / (lastAudioTimestamp / 1000.0f)
    } else {
      0.0f
    }

  val videoDataRate: Float // bps
    get() = if (lastVideoTimestamp > 0) {
      (videoDataSize * 8.0f) / (lastVideoTimestamp / 1000.0f)
    } else {
      0.0f
    }

  /**
   * Resets all accumulated statistics and state variables.
   */
  fun reset() {
    numTags = 0
    numAudioTags = 0
    numVideoTags = 0
    metadataCount = 0

    tagsSize = 0L
    dataSize = 0L
    numFrameTags = 0
    audioTagsSize = 0L
    audioDataSize = 0L
    videoTagsSize = 0L
    videoDataSize = 0L
    fileSize = 0L

    lastTimestamp = 0
    lastAudioTimestamp = 0
    lastVideoTimestamp = 0
    lastKeyframeTimestamp = 0
    lastKeyframeFilePosition = 0L

    resolution = null
    keyframesMap.clear()

    hasAudio = false
    audioInfo = null
    hasVideo = false
    videoInfo = null

    headerSize = 0
  }

  /**
   * Analyzes the FLV header.
   */
  fun analyzeHeader(header: FlvHeader) {
    this.headerSize = header.headerSize
    // Initial file size: Header + PreviousTagSize0 (4 bytes)
    this.fileSize = header.size + FlvParser.POINTER_SIZE.toLong()
  }

  /**
   * Analyzes a single FLV tag, updating statistics and file size.
   */
  fun analyzeTag(tag: FlvTag) {
    // --- Analyze specific tag type ---
    // Note: fileSize *before* this tag is added is passed implicitly to analyze* methods
    // if they need the start offset (like for keyframes).
    when {
      tag.isAudioTag() -> analyzeAudioTag(tag)
      tag.isVideoTag() -> analyzeVideoTag(tag)
      tag.isScriptTag() -> analyzeScriptTag(tag)
      else -> logger.warn("${context.name} Encountered unknown tag type: ${tag.header.tagType}")
    }

    // --- Update general statistics ---
    numTags++
    tagsSize += tag.size // Tag Header + Tag Data size
    dataSize += tag.header.dataSize.toLong() // Tag Data size only
    lastTimestamp = tag.header.timestamp

    // --- Update total file size ---
    // Add the size of the tag itself (header + data) + the PreviousTagSize field (4 bytes) that follows it.
    fileSize += tag.size + FlvParser.POINTER_SIZE.toLong()
  }

  /**
   * Consolidates analyzed data into an FlvMetadataInfo object.
   */
  internal fun makeMetaInfo(): FlvMetadataInfo {
    val currentResolution = resolution ?: DEFAULT_RESOLUTION
    logger.debug("{} metadata tag count: {}", context.name, metadataCount)

    val keyframes = keyframesMap.entries
      .map { FlvKeyframe(it.key, it.value) }
      .sortedBy { it.timestamp }

    // Heuristic: Can seek to end if the last video frame is a keyframe.
    val canSeekToEnd = keyframes.isNotEmpty() && lastVideoTimestamp == lastKeyframeTimestamp

    // Duration in seconds
    val durationSeconds = if (lastTimestamp > 0) lastTimestamp / 1000.0 else 0.0

    val finalLastKeyframePosition = if (keyframes.isNotEmpty()) this.lastKeyframeFilePosition else 0L

    // Calculate rates safely
    val currentAudioDataRate = if (lastAudioTimestamp > 0) audioDataRate else 0.0f
    val currentVideoDataRate = if (lastVideoTimestamp > 0) videoDataRate else 0.0f
    val currentFrameRate = if (lastVideoTimestamp > 0) frameRate else 0.0f

    return FlvMetadataInfo(
      hasAudio = hasAudio,
      hasVideo = hasVideo,
      hasScript = metadataCount > 0,
      hasKeyframes = keyframes.isNotEmpty(),
      canSeekToEnd = canSeekToEnd,
      duration = durationSeconds,
      fileSize = fileSize,
      audioSize = audioTagsSize,
      audioDataSize = audioDataSize,
      audioCodecId = audioInfo?.format,
      audioDataRate = currentAudioDataRate,
      audioSampleRate = audioInfo?.rate,
      audioSampleSize = audioInfo?.soundSize,
      audioSoundType = audioInfo?.type,
      videoSize = videoTagsSize,
      videoDataSize = videoDataSize,
      frameRate = currentFrameRate,
      videoCodecId = videoInfo?.codecId,
      videoDataRate = currentVideoDataRate,
      width = currentResolution.width,
      height = currentResolution.height,
      lastTimestamp = lastTimestamp.toLong(),
      lastKeyframeTimestamp = lastKeyframeTimestamp.toLong(),
      lastKeyframeFilePosition = finalLastKeyframePosition,
      keyframes = keyframes
    )
  }


  private fun analyzeScriptTag(tag: FlvTag) {
    metadataCount++
    if (logger.isTraceEnabled) {
      logger.trace("${context.name} Found script data tag at timestamp ${tag.header.timestamp}")
    }
  }

  private fun analyzeAudioTag(tag: FlvTag) {
    val data = tag.data as AudioData
    if (!hasAudio) {
      hasAudio = true
      // Store config, discard bulk data to save memory
      audioInfo = data.copy(binaryData = byteArrayOf())
      if (logger.isDebugEnabled) {
        logger.debug("{} Audio info detected: {}", context.name, pprint(audioInfo))
      }
    }

    numAudioTags++
    audioTagsSize += tag.size
    audioDataSize += tag.header.dataSize.toLong()
    lastAudioTimestamp = tag.header.timestamp
  }

  private fun analyzeVideoTag(tag: FlvTag) {
    val data = tag.data as VideoData
    val timestamp = tag.header.timestamp.toLong()
    val currentTagStartPosition = fileSize // Capture file size *before* this tag is added

    if (!hasVideo) {
      hasVideo = true
      // Store config, discard bulk data
      videoInfo = data.copy(binaryData = byteArrayOf())
      if (logger.isDebugEnabled) {
        logger.debug("{} Video info detected: {}", context.name, pprint(videoInfo))
      }
    }

    if (tag.isKeyFrame()) {
      // Record keyframe: timestamp -> start position of this tag
      keyframesMap[timestamp] = currentTagStartPosition
      lastKeyframeTimestamp = tag.header.timestamp
      lastKeyframeFilePosition = currentTagStartPosition

      // Detect resolution from sequence header if not already found
      if (tag.isVideoSequenceHeader() && resolution == null) {
        val detectedResolution = data.resolution
        if (detectedResolution.width > 0 && detectedResolution.height > 0) {
          resolution = detectedResolution
          if (logger.isDebugEnabled) {
            logger.debug("{} Video resolution detected: {}x{}", context.name, resolution!!.width, resolution!!.height)
          }
        } else {
          if (logger.isDebugEnabled) {
            logger.debug(
              "${context.name} Video sequence header found, but resolution not available/valid ({}).",
              detectedResolution
            )
          }
        }
      }
    }

    if (!tag.isVideoSequenceHeader()) {
      numFrameTags++
    }

    numVideoTags++
    videoTagsSize += tag.size
    videoDataSize += tag.header.dataSize.toLong()
    lastVideoTimestamp = tag.header.timestamp
  }
}