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

package github.hua0512.flv.data.other

import github.hua0512.flv.data.FlvJoinPoint
import github.hua0512.flv.data.sound.FlvSoundFormat
import github.hua0512.flv.data.sound.FlvSoundRate
import github.hua0512.flv.data.sound.FlvSoundSize
import github.hua0512.flv.data.sound.FlvSoundType
import github.hua0512.flv.data.video.FlvVideoCodecId
import github.hua0512.flv.utils.Keyframe
import kotlinx.serialization.Serializable

/**
 * FLV metadata info
 * @author hua0512
 * @date : 2024/9/8 21:10
 */
@Serializable
data class FlvMetadataInfo(
  val hasAudio: Boolean = false,
  val hasVideo: Boolean = false,
  val hasScript: Boolean = false,
  val hasKeyframes: Boolean = false,
  val canSeekToEnd: Boolean = false,
  val duration: Double = 0.0,
  val fileSize: Long = 0,
  val audioSize: Long = 0,
  val audioDataSize: Long = 0,
  val audioDataRate: Float = 0f,
  val audioCodecId: FlvSoundFormat? = null,
  val audioSampleRate: FlvSoundRate? = null,
  val audioSampleSize: FlvSoundSize? = null,
  val audioSoundType: FlvSoundType? = null,
  val videoSize: Long = 0,
  val videoDataSize: Long = 0,
  val frameRate: Float = 0f,
  val videoCodecId: FlvVideoCodecId? = null,
  val videoDataRate: Float = 0f,
  val width: Int = 0,
  val height: Int = 0,
  val lastTimestamp: Long = 0,
  val lastKeyframeTimestamp: Long = 0,
  val lastKeyframeFilePosition: Long = 0,
  val keyframes: List<Keyframe> = emptyList(),

  val joinPoints: List<FlvJoinPoint> = emptyList(),
)
