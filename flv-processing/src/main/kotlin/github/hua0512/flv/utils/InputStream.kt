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

package github.hua0512.flv.utils

import github.hua0512.flv.FlvReader
import github.hua0512.flv.data.FlvData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.toUInt
import kotlin.use


/**
 * Extension function to read FLV data from an InputStream and emit it as a Flow of FlvData.
 *
 * This function creates an instance of FlvReader to read the FLV header and tags from the InputStream.
 * The read data is emitted as a Flow of FlvData objects.
 *
 * @receiver InputStream The input stream from which to read the FLV data.
 * @return Flow<FlvData> A flow emitting FlvData objects read from the InputStream.
 * @author hua0512
 * @date : 2024/9/9 12:13
 */
fun InputStream.asFlvFlow(): Flow<FlvData> = flow {
  val flvReader = FlvReader(this@asFlvFlow)
  flvReader.use {
    flvReader.readHeader(::emit)
    flvReader.readTags(::emit)
  }
}

/**
 * @author hua0512
 * @date : 2024/6/10 19:31
 */
fun InputStream.readUI24(): UInt = readNBytes(3).let {
  return ((it[0].toUInt() and 0xFFu) shl 16) or
          ((it[1].toUInt() and 0xFFu) shl 8) or
          (it[2].toUInt() and 0xFFu)
}

fun OutputStream.write3BytesInt(value: Int) {
  write((value shr 16).toInt())
  write((value shr 8).toInt())
  write(value)
}

fun DataInputStream.readUnsignedInt(): Int = this.readInt() and 0xFFFFFFFF.toInt()