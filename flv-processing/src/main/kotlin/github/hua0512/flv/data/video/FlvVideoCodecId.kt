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
 * Flv video codec id
 * @author hua0512
 * @date : 2024/6/9 9:31
 */
enum class FlvVideoCodecId(val value: Int) {
  // Unused
  JPEG(1),
  SORENSON_H263(2),
  SCREEN_VIDEO(3),
  ON2_VP6(4),
  ON2_VP6_ALPHA(5),
  SCREEN_VIDEO_V2(6),
  AVC(7),
  EX_HEADER(9),

  // LEGACY HEVC
  HEVC(12);

  companion object {
    fun from(value: Int): FlvVideoCodecId {
      return entries.find { it.value == value }
        ?: throw FlvDataErrorException("Unknown video codec ID: $value")
    }
  }
}