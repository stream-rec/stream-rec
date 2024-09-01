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
 * Huya danmu get living info request
 * Extracted from https://hd2.huya.com/fedbasic/huyabaselibs/taf-signal/taf-signal.global.0.0.12.prod.js
 * @author hua0512
 * @date : 2024/8/17 14:25
 */
data class HuyaGetLivingInfoReq(
  var tId: HuyaUserInfo,
  var lTopSid: Long = 0,
  var lSubSid: Long = 0,
  var lPresenterUid: Long = 0,
  var sTraceSource: String = "",
  var sPassword: String = "",
  var iRoomId: Int = 0,
  var iFreeFollowFlag: Int = 0,
  var iIpStack: Int = 0,
) : TarsStructBase() {

  override fun writeTo(out: TarsOutputStream) {
    with(out) {
      write(tId, 0)
      write(lTopSid, 1)
      write(lSubSid, 2)
      write(lPresenterUid, 3)
      write(sTraceSource, 4)
      write(sPassword, 5)
      write(iRoomId, 6)
      write(iFreeFollowFlag, 7)
      write(iIpStack, 8)
    }
  }

  override fun readFrom(ins: TarsInputStream) {
    with(ins) {
      tId = directRead(tId, 0, false) as HuyaUserInfo
      lTopSid = read(lTopSid, 1, false)
      lSubSid = read(lSubSid, 2, false)
      lPresenterUid = read(lPresenterUid, 3, false)
      sTraceSource = read(sTraceSource, 4, false)
      sPassword = read(sPassword, 5, false)
      iRoomId = read(iRoomId, 6, false)
      iFreeFollowFlag = read(iFreeFollowFlag, 7, false)
      iIpStack = read(iIpStack, 8, false)
    }
  }

  override fun newInit(): TarsStructBase = this.copy()
}