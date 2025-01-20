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

/**
 * FLV video frame type
 * @author hua0512
 * @date : 2024/6/9 9:38
 */
enum class FlvVideoFrameType(val value: Int) {
  // Key frame (for AVC, a seekable frame)
  KEY_FRAME(1),

  // Inter frame, for AVC, a non-key seekable frame
  INTER_FRAME(2),

  // Disposable inter frame, H.263 only
  DISPOSABLE_INTER_FRAME(3),

  // Generated key frame, reserved for server use only
  GENERATED_KEY_FRAME(4),

  // If videoFrameType is not ignored and is set to VideoFrameType.Command,         ¦
  // the payload will not contain video data. Instead, (Ex)VideoTagHeader           ¦
  // will be followed by a UI8, representing the following meanings:                ¦
  //                                                                                ¦
  //     0 = Start of client-side seeking video frame sequence                      ¦
  //     1 = End of client-side seeking video frame sequence                        ¦
  //                                                                                ¦
  // frameType is ignored if videoPacketType is VideoPacketType.MetaData            ¦
  // Video info/command frame
  VIDEO_INFO_FRAME(5),

  RESERVED(6),
  RESERVED2(7);


  companion object {
    fun from(value: Int): FlvVideoFrameType? {
      return FlvVideoFrameType.entries.find { it.value == value }
    }
  }
}