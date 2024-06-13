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
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DouyinQualitySerializer::class)
enum class DouyinQuality(val value: String) {

  // 原画origin
  origin("origin"),

  // 蓝光uhd
  uhd("uhd"),

  // 超清hd
  hd("hd"),

  // 高清sd
  sd("sd"),

  // 标清ld
  ld("ld"),

  // 流畅
  md("md"),

  // only audio
  audio("ao"),

}


object DouyinQualitySerializer : KSerializer<DouyinQuality> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DouyinQuality", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): DouyinQuality {
    val value = decoder.decodeString()
    return DouyinQuality.entries.first { it.value == value }
  }

  override fun serialize(encoder: Encoder, value: DouyinQuality) {
    encoder.encodeString(value.value)
  }
}