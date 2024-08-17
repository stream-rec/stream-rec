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

package github.hua0512.plugins.huya.danmu.msg.req

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

/**
 * Huya websocket user info
 *
 * Extracted from https://hd2.huya.com/fedbasic/huyabaselibs/taf-signal/taf-signal.global.0.0.12.prod.js
 * @author hua0512
 * @date : 2024/2/10 18:26
 */
data class HuyaUserInfo(
  var lUid: Long = 0,
  var sGuid: String = "",
  var sToken: String = "",
  var sUA: String = "",
  var sCookie: String = "",
  var iTokenType: Int = 0,
  var sDeviceInfo: String = "",
  var sQIMEI: String = "",
) : TarsStructBase() {


  override fun readFrom(ins: TarsInputStream) {
    ins.apply {
      lUid = read(lUid, 0, true)
      sGuid = read(sGuid, 1, true)
      sToken = read(sToken, 2, true)
      sUA = read(sUA, 3, true)
      sCookie = read(sCookie, 4, true)
      iTokenType = read(iTokenType, 5, true)
      sDeviceInfo = read(sDeviceInfo, 6, true)
      sQIMEI = read(sQIMEI, 7, true)
    }
  }

  override fun writeTo(out: TarsOutputStream) {
    out.apply {
      write(lUid, 0)
      write(sGuid, 1)
      write(sToken, 2)
      write(sUA, 3)
      write(sCookie, 4)
      write(iTokenType, 5)
      write(sDeviceInfo, 6)
      write(sQIMEI, 7)
    }
  }


  override fun newInit(): TarsStructBase = this.copy()

}