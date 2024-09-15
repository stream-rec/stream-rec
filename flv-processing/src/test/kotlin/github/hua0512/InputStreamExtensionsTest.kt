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

package github.hua0512;

import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.utils.asFlvFlow
import github.hua0512.flv.utils.createEndOfSequenceTag
import github.hua0512.flv.utils.isAvcEndSequence
import github.hua0512.flv.utils.readUI24
import github.hua0512.flv.utils.writeInt
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.expect

class InputStreamExtensionsTest {

  @Test
  fun readUI24_readsCorrectValue() {
    val inputStream: InputStream = ByteArrayInputStream(byteArrayOf(0x12, 0x34, 0x56))
    val result = inputStream.readUI24()
    expect(0x123456u) {
      result
    }
  }

  @Test
  fun readUI24_readsZero() {
    val inputStream: InputStream = ByteArrayInputStream(byteArrayOf(0x00, 0x00, 0x00))
    val result = inputStream.readUI24()
    expect(0x000000u) {
      result
    }
  }

  @Test
  fun readUI24_readsMaxValue() {
    val inputStream: InputStream = ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
    val result = inputStream.readUI24()
    expect(0xFFFFFFu) {
      result
    }
  }

  @Test
  fun readUI24_handlesEndOfStream() {
    val inputStream: InputStream = ByteArrayInputStream(byteArrayOf(0x12, 0x34))
    assertThrows<ArrayIndexOutOfBoundsException> {
      inputStream.readUI24()
    }
  }

  @Test
  fun asFlvFlow_readsFlvDataCorrectly() = runBlocking {
    val inputStream = ByteArrayInputStream(
      byteArrayOf(
        0x46, 0x4C, 0x56, 0x01, 0x05, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00
      )
    )
    val result = inputStream.asFlvFlow().toList()
    expect(1) {
      result.size
    }
    val flvData = result[0] as FlvHeader
    expect(0x01) {
      flvData.version
    }
    expect(0x05) {
      flvData.flags.value
    }
    expect(0x09) {
      flvData.headerSize
    }
  }

  @Test
  fun asFlvFlow_handlesEOFException(): Unit = runBlocking {
    val inputStream = ByteArrayInputStream(byteArrayOf(0x46, 0x4C, 0x56, 0x01, 0x05, 0x00, 0x00))

    val result = inputStream.asFlvFlow().toList()
    expect(0) {
      result.size
    }
  }

  @Test
  fun asFlvFlow_handlesFlvErrorException(): Unit = runBlocking {
    val inputStream = ByteArrayInputStream(byteArrayOf(0x46, 0x4C, 0x56, 0x01, 0xFF.toByte(), 0x00, 0x00, 0x00, 0x09))

    val result = inputStream.asFlvFlow().toList()
    expect(0) {
      result.size
    }
  }

  @Test
  fun asFlvFlow_emitsEndOfSequenceTag() = runBlocking {
    val baos = ByteArrayOutputStream()
    val eosTag = createEndOfSequenceTag(1, 0, 0).apply {
      header.write(baos)
      data.write(baos)
      baos.writeInt(size.toInt())
    }

    val inputStream = ByteArrayInputStream(
      byteArrayOf(
        0x46,
        0x4C,
        0x56,
        0x01,
        0x05,
        0x00,
        0x00,
        0x00,
        0x09,
        0x00,
        0x00,
        0x00,
        0x00,
      ) + baos.toByteArray()
    )
    val result = inputStream.asFlvFlow().toList()
    expect(2) {
      result.size
    }
    val flvData = result[1] as FlvTag
    expect(eosTag.header.dataSize) {
      flvData.header.dataSize
    }
    expect(eosTag.header.streamId) {
      flvData.header.streamId.toInt()
    }
    expect(eosTag.header.timestamp) {
      flvData.header.timestamp
    }
    expect(eosTag.num) {
      flvData.num
    }
    expect(true) {
      flvData.isAvcEndSequence()
    }
  }
}