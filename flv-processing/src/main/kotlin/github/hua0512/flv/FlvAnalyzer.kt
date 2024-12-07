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
 * FLV analyzer
 * @author hua0512
 * @date : 2024/9/8 21:18
 */
class FlvAnalyzer(val context: StreamerContext) {


  companion object {

    private const val TAG = "FlvAnalyzer"
    private val logger = logger(TAG)

  }

  private var numTags = 0
  private var numAudioTags = 0
  private var numVideoTags = 0


  private var tagsSize: Long = 0
  private var dataSize: Long = 0
  private var audioTagsSize: Long = 0
  private var audioDataSize: Long = 0
  private var videoTagsSize: Long = 0
  private var videoDataSize: Long = 0
  private var lastTimestamp: Int = 0
  private var lastAudioTimestamp: Int = 0
  private var lastVideoTimestamp: Int = 0

  private var resolution: VideoResolution? = null
  private var keyframesMap = mutableMapOf<Long, Long>()
  private var lastKeyframeTimestamp: Int = 0
  private var hasAudio = false
  private var audioInfo: AudioData? = null
  private var duration = 0


  private var hasVideo = false
  private var videoInfo: VideoData? = null

  private var headerSize = 0

  var fileSize: Long = 0
  var metadataCount = 0

  val frameRate: Float
    get() = if (lastVideoTimestamp > 0) {
      numVideoTags.toFloat() * 1000 / lastVideoTimestamp
    } else {
      0.0f
    }

  val audioDataRate: Float
    get() = if (lastAudioTimestamp > 0) {
      audioDataSize * 8f / lastAudioTimestamp
    } else {
      0.0f
    }

  val videoDataRate: Float
    get() {
      return try {
        videoDataSize * 8f / lastVideoTimestamp
      } catch (e: Exception) {
        0.0f
      }
    }

  internal fun makeMetaInfo(): FlvMetadataInfo {
    resolution = resolution ?: VideoResolution(0, 0)
    logger.debug("{} metadata count: {}", context.name, metadataCount)
    val keyframes = keyframesMap.map { (timestamp, position) -> FlvKeyframe(timestamp, position) }.sortedBy { it.timestamp }
    return FlvMetadataInfo(
      hasAudio = hasAudio,
      hasVideo = hasVideo,
      hasScript = true,
      hasKeyframes = keyframes.isNotEmpty(),
      canSeekToEnd = lastVideoTimestamp == lastKeyframeTimestamp,
      duration = lastTimestamp / 1000.0,
      fileSize = fileSize,
      audioSize = audioTagsSize,
      audioDataSize = audioDataSize,
      audioCodecId = audioInfo?.format,
      audioDataRate = audioDataRate,
      audioSampleRate = audioInfo?.rate,
      audioSampleSize = audioInfo?.soundSize,
      audioSoundType = audioInfo?.type,
      videoSize = videoTagsSize,
      videoDataSize = videoDataSize,
      frameRate = frameRate,
      videoCodecId = videoInfo?.codecId,
      videoDataRate = videoDataRate,
      width = resolution!!.width,
      height = resolution!!.height,
      lastTimestamp = lastTimestamp.toLong(),
      lastKeyframeTimestamp = lastKeyframeTimestamp.toLong(),
      lastKeyframeFilePosition = keyframesMap[lastKeyframeTimestamp.toLong()] ?: 0,
      keyframes = keyframes
    )

  }


  fun reset() {
    numTags = 0
    numAudioTags = 0
    numVideoTags = 0
    tagsSize = 0
    dataSize = 0
    audioTagsSize = 0
    audioDataSize = 0
    videoTagsSize = 0
    videoDataSize = 0
    lastTimestamp = 0
    lastAudioTimestamp = 0
    lastVideoTimestamp = 0
    resolution = null
    keyframesMap.clear()
    lastKeyframeTimestamp = 0
    hasAudio = false
    audioInfo = null
    hasVideo = false
    videoInfo = null
    headerSize = 0
    fileSize = 0
    metadataCount = 0
    duration = 0
  }

  fun analyzeHeader(header: FlvHeader) {
    this.headerSize = header.headerSize
    this.fileSize += header.size + FlvParser.POINTER_SIZE
  }

  fun analyzeTag(tag: FlvTag) {
    when {
      tag.isAudioTag() -> analyzeAudioTag(tag)
      tag.isVideoTag() -> analyzeVideoTag(tag)
      tag.isScriptTag() -> analyzeScriptTag(tag)
      else -> throw IllegalArgumentException("Unknown tag type: ${tag.header.tagType}")
    }

    numTags++
    tagsSize += tag.size
    dataSize += tag.header.dataSize.toLong()
    lastTimestamp = tag.header.timestamp
    fileSize += tag.size + FlvParser.POINTER_SIZE
  }

  private fun analyzeScriptTag(tag: FlvTag) {
    // do nothing
    tag.data as ScriptData
    metadataCount++
    return
  }


  private fun analyzeAudioTag(tag: FlvTag) {
    tag.data as AudioData
    if (!hasAudio) {
      hasAudio = true
      audioInfo = tag.data.copy(binaryData = byteArrayOf())
      logger.debug("{} Audio info: {}", context.name, pprint(audioInfo))
    }

    numAudioTags++
    audioTagsSize += tag.size
    audioDataSize += tag.header.dataSize
    lastAudioTimestamp = tag.header.timestamp
  }

  private fun analyzeVideoTag(tag: FlvTag) {

    tag.data as VideoData

    if (tag.isKeyFrame()) {
      keyframesMap[tag.header.timestamp.toLong()] = fileSize
      if (tag.isVideoSequenceHeader() && resolution == null) {
        resolution = tag.data.resolution
        logger.debug("{} Video resolution: {}", context.name, pprint(resolution))
      }
      lastKeyframeTimestamp = tag.header.timestamp
    }

    if (videoInfo == null) {
      hasVideo = true
      videoInfo = tag.data.copy(binaryData = byteArrayOf())
      logger.debug("{} Video info: {}", context.name, pprint(videoInfo))
    }

    numVideoTags++
    videoTagsSize += tag.size
    videoDataSize += tag.header.dataSize
    lastVideoTimestamp = tag.header.timestamp
  }


}