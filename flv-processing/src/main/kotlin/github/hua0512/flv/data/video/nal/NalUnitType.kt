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

package github.hua0512.flv.data.video.nal

/**
 * Base interface for NAL unit types
 */
interface NalUnitType {
  val value: Int
  val msg: String

  /**
   * Returns true if this NAL unit type represents a VCL (Video Coding Layer) NAL unit
   */
  fun isVcl(): Boolean

  /**
   * Returns true if this NAL unit type represents an IDR (Instantaneous Decoding Refresh) picture
   */
  fun isIdr(): Boolean

  /**
   * Returns true if this NAL unit type represents a Video Parameter Set (VPS)
   * Note: Only applicable for HEVC, will always return false for AVC
   */
  fun isVps(): Boolean = false

  /**
   * Returns true if this NAL unit type represents a Sequence Parameter Set (SPS)
   */
  fun isSps(): Boolean = false

  /**
   * Returns true if this NAL unit type represents a Picture Parameter Set (PPS)
   */
  fun isPps(): Boolean = false

  /**
   * Returns true if this NAL unit type represents a key frame (IDR or parameter sets)
   */
  fun isKeyFrame(): Boolean = isIdr() || isVps() || isSps() || isPps()
} 