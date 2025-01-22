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

package github.hua0512.flv.data.tag

import github.hua0512.flv.data.amf.AmfValue
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

/**
 * A script tag data, usually used for metadata
 * @author hua0512
 * @date : 2024/6/8 19:06
 */
@Serializable
data class FlvScriptTagData(val values: List<AmfValue> = emptyList()) : FlvTagData {

  val valuesCount: Int
    get() = values.size

  override val binaryData: ByteArray
    get() = toByteArray()

  override val headerSize: Int = 0

  val bodySize: Int
    get() = values.sumOf { it.size }

  override val size: Int
    get() = bodySize

  operator fun get(index: Int): AmfValue = values[index]

  override fun write(sink: Sink) {
    values.forEach { it.write(sink) }
  }

  fun toByteArray(): ByteArray {
    return Buffer().apply {
      write(this)
    }.readByteArray()
  }

  fun validateSize() {
    val serialized = toByteArray()
    val actualSize = serialized.size
    if (actualSize != size) {
      throw IllegalStateException(
        "FlvScriptTagData size mismatch: calculated=$size, actual=$actualSize\n" +
                "Values: ${values.joinToString { "${it::class.simpleName}(size=${it.size})" }}"
      )
    }
  }

  override fun toString(): String {
    return "FlvScriptTagData(values=${
      values.joinToString { "${it::class.simpleName}(size=${it.size})" }
    }, count=$valuesCount, size=$size)"
  }

}