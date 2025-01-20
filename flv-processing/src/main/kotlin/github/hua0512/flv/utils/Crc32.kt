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

package github.hua0512.flv.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSink

/**
 * CRC32 SINK
 * From: [kotlinx rawSinkSample](https://github.com/Kotlin/kotlinx-io/blob/master/core/common/test/samples/rawSinkSample.kt)
 * @author hua0512
 * @date : 2024/11/30 22:01
 */
class CRC32Sink(private val upstream: RawSink) : RawSink {
  private val tempBuffer = Buffer()

  @OptIn(ExperimentalUnsignedTypes::class)
  private val crc32Table = generateCrc32Table()
  private var crc32: UInt = 0xffffffffU

  private fun update(value: Byte) {
    val index = value.toUInt().xor(crc32).toUByte()
    crc32 = crc32Table[index.toInt()].xor(crc32.shr(8))
  }

  fun crc32(): UInt = crc32.xor(0xffffffffU)

  override fun write(source: Buffer, byteCount: Long) {
    source.copyTo(tempBuffer, 0, byteCount)

    while (!tempBuffer.exhausted()) {
      update(tempBuffer.readByte())
    }

    upstream.write(source, byteCount)
  }

  override fun flush() = upstream.flush()

  override fun close() = upstream.close()

  private fun generateCrc32Table(): UIntArray {
    val table = UIntArray(256)

    for (idx in table.indices) {
      table[idx] = idx.toUInt()
      for (bit in 8 downTo 1) {
        table[idx] = if (table[idx] % 2U == 0U) {
          table[idx].shr(1)
        } else {
          table[idx].shr(1).xor(0xEDB88320U)
        }
      }
    }

    return table
  }
}