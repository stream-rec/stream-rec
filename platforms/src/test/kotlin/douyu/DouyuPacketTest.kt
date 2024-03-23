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

package douyu

import github.hua0512.plugins.douyu.danmu.DouyuPacket
import github.hua0512.plugins.douyu.danmu.DouyuSTT
import kotlin.test.Test

/**
 * @author hua0512
 * @date : 2024/3/23 13:45
 */
class DouyuPacketTest {


  @Test
  fun testConcat() {
    val result = DouyuPacket.concat(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
    assert(result.contentEquals(byteArrayOf(1, 2, 3, 4, 5, 6)))
  }

  private val heartbeat = byteArrayOf(
    0x14,
    0x00,
    0x00,
    0x00,
    0x14,
    0x00,
    0x00,
    0x00,
    0xb1.toByte(),
    0x02,
    0x00,
    0x00,
    0x74,
    0x79,
    0x70,
    0x65,
    0x40,
    0x3d,
    0x6d,
    0x72,
    0x6b,
    0x6c,
    0x2f,
    0x00
  )

  @Test
  fun testEncode() {
    val result = DouyuPacket.encode(DouyuSTT.serialize(mapOf("type" to "mrkl")))
    assert(result.contentEquals(heartbeat))
  }

  @Test
  fun testDecode() {
    val result = DouyuPacket.decode(heartbeat)
    assert(result.size == 1)
    assert(result.first() == "type@=mrkl/")
  }

}