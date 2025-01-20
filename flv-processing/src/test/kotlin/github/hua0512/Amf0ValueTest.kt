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

import github.hua0512.flv.data.amf.AmfValue.Amf0Value
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.expect

class Amf0ValueTest {

  @Test
  fun nullValue_writesCorrectBytes() {
    val buffer = Buffer()
    Amf0Value.Null.write(buffer)
    expect(1) { buffer.size }
    expect(true) {
      byteArrayOf(0x05).contentEquals(buffer.readByteArray())
    }
  }

  @Test
  fun numberValue_writesCorrectBytes() {
    val buffer = Buffer()
    val number = 1234.5678
    Amf0Value.Number(number).write(buffer)
    expect(9) { buffer.size }
    val bytes = buffer.readByteArray()
    expect(true) {
      bytes[0] == 0x00.toByte()
    }

    expect(true) {
      val amfNumber = byteArrayOf(
        0x00.toByte(), // Type marker for Number
        0x40.toByte(), 0x93.toByte(), 0x4A.toByte(), 0x45.toByte(),
        0x6D.toByte(), 0x5C.toByte(), 0xFA.toByte(), 0xAD.toByte()
      )
      amfNumber.contentEquals(bytes)
    }

    expect(true) {
      // parse the bytes to a double
      val parsedNumber = bytes.sliceArray(1 until 9)
      // build double from bytes
      val longBits = parsedNumber.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xff) }
      val double = java.lang.Double.longBitsToDouble(longBits)
      double == number
    }
  }

  @Test
  fun booleanValue_writesCorrectBytes() {
    val buffer = Buffer()
    Amf0Value.Boolean(true).write(buffer)
    expect(true) {
      byteArrayOf(0x01, 0x01).contentEquals(buffer.readByteArray())
    }
  }

  @Test
  fun stringValue_writesCorrectBytes() {
    val buffer = Buffer()
    Amf0Value.String("test").write(buffer)
    expect(true) {
      byteArrayOf(0x02, 0x00, 0x04, 0x74, 0x65, 0x73, 0x74).contentEquals(buffer.readByteArray())
    }
  }

  @Test
  fun objectValue_writesCorrectBytes() {
    val outputStream = Buffer()
    val properties = mapOf("key" to Amf0Value.String("value"))
    Amf0Value.Object(properties).write(outputStream)
    expect(true) {
      byteArrayOf(
        0x03,
        0x00,
        0x03,
        0x6B,
        0x65,
        0x79,
        0x02,
        0x00,
        0x05,
        0x76,
        0x61,
        0x6C,
        0x75,
        0x65,
        0x00,
        0x00,
        0x09
      ).contentEquals(outputStream.readByteArray())
    }
  }

  @Test
  fun ecmaArrayValue_writesCorrectBytes() {
    val buffer = Buffer()
    val properties = mapOf("name" to Amf0Value.String("John"), "age" to Amf0Value.Number(30.0))
    Amf0Value.EcmaArray(properties).write(buffer)
    val ecmaArray = byteArrayOf(
      0x08,                   // Type marker for ECMA Array
      0x00, 0x00, 0x00, 0x02, // Number of key-value pairs (2)

      // First key-value pair
      0x00, 0x04,             // Key length (4)
      0x6E, 0x61, 0x6D, 0x65, // Key "name"
      0x02,                   // Type marker for String
      0x00, 0x04,             // String length (4)
      0x4A, 0x6F, 0x68, 0x6E, // Value "John"

      // Second key-value pair
      0x00, 0x03,             // Key length (3)
      0x61, 0x67, 0x65,       // Key "age"
      0x00,                   // Type marker for Number
      0x40, 0x3E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Value 30 (double-precision)

      // End of object marker
      0x00, 0x00, 0x09
    )
    expect(true) {
      ecmaArray.contentEquals(buffer.readByteArray())
    }
  }

  @Test
  fun strictArrayValue_writesCorrectBytes() {
    val buffer = Buffer()
    val values = listOf(Amf0Value.String("value"))
    Amf0Value.StrictArray(values).write(buffer)

    expect(true) {
      byteArrayOf(
        0x0A,
        0x00,
        0x00,
        0x00,
        0x01,
        0x02,
        0x00,
        0x05,
        0x76,
        0x61,
        0x6C,
        0x75,
        0x65
      ).contentEquals(buffer.readByteArray())
    }
  }

}