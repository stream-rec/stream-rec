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

package github.hua0512.data.platform

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Douyu stream quality options.
 * @author hua0512
 * @date : 2024/3/22 21:52
 */
enum class DouyuQuality(val rate: Int) {
  // ORIGIN quality, could be 30 or 60 fps, more than 4M bitrate
  // 原画1080P30/60 码率比4M高，每个主播不一样
  ORIGIN(0),

  // ULTRA HIGH DEFINITION quality, capped at 4000 bitrate
  // 蓝光4M
  UHD(4),

  // HIGH DEFINITION quality, capped at 2000 bitrate
  // 超清 码率大概是2M
  HD(3),

  // STANDARD DEFINITION quality, capped at 900 bitrate
  // 高清 码率大概是900K （真不要脸）
  SD(2),

  // LOW DEFINITION quality, capped at 500 bitrate
  // 流畅 码率大概是500K
  LD(1),
}

object DouyuQualitySerializer : KSerializer<DouyuQuality> {
  override val descriptor = PrimitiveSerialDescriptor("DouyuQuality", PrimitiveKind.INT)

  override fun serialize(encoder: Encoder, value: DouyuQuality) {
    encoder.encodeInt(value.rate)
  }

  override fun deserialize(decoder: Decoder): DouyuQuality {
    val rate = decoder.decodeInt()
    return DouyuQuality.entries.first { it.rate == rate }
  }
}