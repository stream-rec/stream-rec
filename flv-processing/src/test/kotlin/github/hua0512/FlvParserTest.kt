package github.hua0512/*
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

import github.hua0512.flv.FlvParser
import github.hua0512.flv.data.FlvHeaderFlags
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

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

/**
 * @author hua0512
 * @date : 2024/6/9 10:29
 */
class FlvParserTest {


  @Test
  fun testParseHeader() = runTest {
    val buffer = Buffer()
    buffer.write(byteArrayOf(0x46, 0x4c, 0x56, 0x01, 0x05, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00))
    val parser = FlvParser(buffer)
    val header = parser.parseHeader()
    assertEquals("FLV", header.signature)
    assertEquals(1, header.version)
    assertEquals(FlvHeaderFlags(0x05), header.flags)
    assertEquals(9, header.headerSize)
    println(header)
  }

  @Test
  fun testParseHeaderFail() = runTest {
    val buffer = Buffer()
    buffer.write(byteArrayOf(0x46, 0x4c, 0x56, 0x01, 0x05, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00))
    val parser = FlvParser(buffer)
    try {
      parser.parseHeader()
    } catch (e: Exception) {
      assertEquals("Invalid FLV header size: 8", e.message)
    }
  }
}