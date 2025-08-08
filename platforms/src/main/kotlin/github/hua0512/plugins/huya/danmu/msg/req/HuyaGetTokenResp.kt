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

package github.hua0512.plugins.huya.danmu.msg.req

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase


data class HuyaGetTokenResp(
  var url: String = "",
  var cdnType: String = "",
  var streamName: String = "",
  var presenterUid: Long = 0,
  var antiCode: String = "",
  var sTime: String = "",
  var flvAntiCode: String = "",
  var hlsAntiCode: String = "",
) : TarsStructBase() {

  constructor() : this("", "", "", 0, "", "", "", "")

  override fun writeTo(os: TarsOutputStream) {
    with(os) {
      write(url, 0)
      write(cdnType, 1)
      write(streamName, 2)
      write(presenterUid, 3)
      write(antiCode, 4)
      write(sTime, 5)
      write(flvAntiCode, 6)
      write(hlsAntiCode, 7)
    }
  }

  override fun readFrom(`is`: TarsInputStream) {
    with(`is`) {
      this@HuyaGetTokenResp.url = read(url, 0, false)
      this@HuyaGetTokenResp.cdnType = read(cdnType, 1, false)
      this@HuyaGetTokenResp.streamName = read(streamName, 2, false)
      this@HuyaGetTokenResp.presenterUid = read(presenterUid, 3, false)
      this@HuyaGetTokenResp.antiCode = read(antiCode, 4, false)
      this@HuyaGetTokenResp.sTime = read(sTime, 5, false)
      this@HuyaGetTokenResp.flvAntiCode = read(flvAntiCode, 6, false)
      this@HuyaGetTokenResp.hlsAntiCode = read(hlsAntiCode, 7, false)

    }
  }


}