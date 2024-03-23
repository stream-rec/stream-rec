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

package github.hua0512.plugins.douyu.danmu

/**
 * Douyu danmu packet
 * @author hua0512
 * @date : 2024/3/23 13:34
 */

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class DouyuPacket {

  companion object {
    private const val HEADER_LEN_SIZE = 4
    private const val HEADER_LEN_TYPECODE = 2
    private const val HEADER_LEN_ENCRYPT = 1
    private const val HEADER_LEN_PLACEHOLDER = 1
    private const val HEADER_LEN_TOTAL = HEADER_LEN_SIZE * 2 + HEADER_LEN_TYPECODE + HEADER_LEN_ENCRYPT + HEADER_LEN_PLACEHOLDER

    private const val CLIENT_MESSAGE_TYPE = 689
    internal const val SERVER_MESSAGE_TYPE = 690

    fun concat(vararg buffers: ByteArray): ByteArray {
      val totalLength = buffers.sumOf { it.size }
      val result = ByteArray(totalLength)
      var offset = 0
      for (buffer in buffers) {
        System.arraycopy(buffer, 0, result, offset, buffer.size)
        offset += buffer.size
      }
      return result
    }

    fun encode(data: String): ByteArray {
      val body = concat(data.toByteArray(), byteArrayOf(0))
      val messageLength = body.size + HEADER_LEN_SIZE * 2
      val buffer = ByteBuffer.allocate(body.size + HEADER_LEN_TOTAL).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        putInt(messageLength)
        putInt(messageLength)
        putShort(CLIENT_MESSAGE_TYPE.toShort())
        putShort(0.toShort())
        put(body)
      }
      return buffer.array()
    }

    fun decode(data: ByteArray): List<String> {
      val messages = mutableListOf<String>()
      var offset = 0

      while (offset < data.size) {
        if (data.size - offset < HEADER_LEN_TOTAL) {
          throw IllegalArgumentException("Invalid data size: ${data.size - offset}")
        }

        val buffer = ByteBuffer.wrap(data, offset, data.size - offset).order(ByteOrder.LITTLE_ENDIAN)
        val messageLength = buffer.int
        val messageLength2 = buffer.int
        val typeCode = buffer.short
        val encrypt = buffer.get()
        val placeholder = buffer.get()
        val bodySize = messageLength - HEADER_LEN_SIZE * 2 - 1
        val body = ByteArray(bodySize)
        buffer.get(body, 0, bodySize)
        buffer.get() // terminating byte
        messages.add(String(body))

        offset += messageLength + HEADER_LEN_SIZE
      }

      return messages
    }

  }


}
