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

package github.hua0512.flv.data

import github.hua0512.flv.exceptions.FlvHeaderErrorException
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlinx.serialization.Serializable

/**
 * FLV header data class
 * @property signature FLV signature "FLV", always 3 bytes
 * @property version FLV version, always 1 byte
 * @property flags FLV flags, always 1 byte
 * @property headerSize FLV header size, always 4 bytes
 */
@Serializable
data class FlvHeader(val signature: String, val version: Int, val flags: FlvHeaderFlags, val headerSize: Int, override val crc32: Long) : FlvData {

  init {
    if (signature.length != 3 || signature != SIGNATURE) {
      throw FlvHeaderErrorException("Invalid FLV signature: $signature")
    }

    if (version != SIGNATURE_VERSION) {
      throw FlvHeaderErrorException("Invalid FLV version: $version")
    }

    if (headerSize != HEADER_SIZE) {
      throw FlvHeaderErrorException("Invalid FLV header size: $headerSize")
    }
  }

  override val size = headerSize.toLong()


  fun write(sink: Sink) {
    val buffer = Buffer()
    with(buffer) {
      // write 'FLV' signature
      writeString(signature)
      writeByte(version.toByte())
      writeByte(flags.value.toByte())
      // write header size as Int (4 bytes)
      writeInt(headerSize)
    }
    buffer.transferTo(sink)
    sink.flush()
  }

  companion object {

    internal const val SIGNATURE = "FLV"

    internal const val SIGNATURE_VERSION = 1

    internal const val HEADER_SIZE = 9

    /**
     * Create a default FLV header
     * @return FLV header
     */
    fun default() = FlvHeader(SIGNATURE, SIGNATURE_VERSION, FlvHeaderFlags(5), HEADER_SIZE, 265716093)
  }
}