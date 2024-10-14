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

import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.utils.ScriptData
import github.hua0512.utils.logger
import kotlinx.io.Source


internal typealias onTagRead = suspend (FlvData) -> Unit

/**
 * FLV reader
 * @author hua0512
 * @date : 2024/6/9 12:05
 */
internal class FlvReader(val source: Source) : AutoCloseable {

  companion object {
    private const val TAG = "FlvReader"
    val logger = logger(TAG)
  }

  private var parser: FlvParser = FlvParser(source)

  private lateinit var header: FlvHeader

  suspend fun readHeader(onTagRead: onTagRead) {
    if (::header.isInitialized) {
      logger.error("Header already read")
      throw FlvDataErrorException("Header already read")
    }
    header = parser.parseHeader()
    val previousTagSize = parser.parsePreviousTagSize()
    if (previousTagSize != 0) {
      logger.error("First previous tag size must be 0, but got $previousTagSize")
      throw FlvDataErrorException("First previous tag size must be 0, but got $previousTagSize")
    }
    onTagRead(header)
  }

  suspend fun readTag(): FlvTag {
    val tag = parser.parseTag()
    val previousTagSize = parser.parsePreviousTagSize()
    if (previousTagSize != tag.size.toInt()) {
      if (tag.data is ScriptData) {
        logger.warn("Ignored mismatched previous tag size for script tag, expect ${tag.size}, but got $previousTagSize")
        return tag
      }
      logger.error("Previous tag size not match, expect ${tag.size}, but got $previousTagSize")
      throw FlvDataErrorException("Previous tag size not match, expect ${tag.size}, but got $previousTagSize")
    }
    return tag
  }


  suspend inline fun readTags(onTagRead: onTagRead) {
    while (true) {
      try {
        onTagRead(readTag())
      } catch (e: Exception) {
        logger.error("Read tag error: {}", e.message)
        throw e
      }
    }
  }

  suspend fun readAll(onTagRead: onTagRead) {
    if (!::header.isInitialized) {
      readHeader(onTagRead)
    }
    readTags(onTagRead)
  }



  override fun close() {
    source.close()
  }


}