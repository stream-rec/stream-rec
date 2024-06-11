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

package github.hua0512.flv.data.avc;

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

internal class StructReader(input: InputStream) : AutoCloseable {

  private val dataInput = DataInputStream(input)

  fun readUI8(): Int {
    return dataInput.readUnsignedByte()
  }

  fun readUI16(): Int {
    return dataInput.readUnsignedShort()
  }

  fun readBytes(length: Int): ByteArray {
    val availableBytes = dataInput.available()
    // Check if the required bytes are greater than the available bytes.
    if (length > availableBytes) {
      throw EOFException("Trying to read $length bytes but only $availableBytes are available.")
    }

    val byteArray = ByteArray(length)
    dataInput.readFully(byteArray)
    return byteArray
  }

  override fun close() {
    dataInput.close()
  }
}