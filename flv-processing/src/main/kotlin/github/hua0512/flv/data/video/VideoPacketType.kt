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

package github.hua0512.flv.data.video

import github.hua0512.flv.exceptions.FlvDataErrorException


/**
 * Video packet type
 * @author hua0512
 * @date : 2025/1/9 9:52
 */
enum class VideoPacketType(val value: Int) {
  SEQUENCE_HEADER(0),     // AVC/HEVC sequence header
  NALU(1),               // AVC/HEVC NALU
  END_OF_SEQUENCE(2),    // AVC/HEVC end of sequence
  METADATA(3),           // Codec-specific metadata
  MPEG2TS_SEQUENCE_START(4), // MPEG2-TS sequence start
  CODED_FRAMES(5),       // Coded frames
  CODED_FRAMES_X(6),     // Coded frames with composition time = 0
  ;

  companion object {
    fun from(value: Int): VideoPacketType {
      return entries.find { it.value == value }
        ?: throw FlvDataErrorException("Unknown video packet type: $value")
    }
  }
} 