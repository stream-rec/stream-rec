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

package github.hua0512.flv

import github.hua0512.flv.FlvParser.Companion.POINTER_SIZE
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import kotlinx.io.Sink

/**
 * FLV writer, write FLV file
 * @author hua0512
 * @date : 2024/6/10 19:51
 */
internal class FlvWriter(val sink: Sink) : AutoCloseable {

  private var dumper: FlvDumper = FlvDumper(sink)

  fun writeHeader(header: FlvHeader): Int {
    dumper.dumpHeader(header)
    dumper.dumpPreviousTagSize(0)
    return header.headerSize + POINTER_SIZE
  }

  fun writeTag(tag: FlvTag): Int {
    dumper.dumpTag(tag)
    dumper.dumpPreviousTagSize(tag.size.toInt())
    return tag.size.toInt() + POINTER_SIZE
  }

  fun writeTags(tags: List<FlvTag>) {
    tags.sumOf { writeTag(it) }
  }

  override fun close() {
    sink.close()
  }
}