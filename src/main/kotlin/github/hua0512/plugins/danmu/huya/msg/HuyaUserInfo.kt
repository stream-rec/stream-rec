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

package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

/**
 * Huya websocket user info
 * @author hua0512
 * @date : 2024/2/10 18:26
 */
data class HuyaUserInfo(
  var lUid: Long = 0,
  var bAnonymous: Boolean = true,
  var sGuid: String = "",
  var sToken: String = "",
  var lTid: Long = 0,
  var lSid: Long = 0,
  var lGroupId: Long = 0,
  var lGroupType: Long = 0,
  var sAppId: String = "",
  var sUA: String = "",
) : TarsStructBase() {
  override fun writeTo(os: TarsOutputStream) {
    with(os) {
      write(lUid, 0)
      write(bAnonymous, 1)
      write(sGuid, 2)
      write(sToken, 3)
      write(lTid, 4)
      write(lSid, 5)
      write(lGroupId, 6)
      write(lGroupType, 7)
      write(sAppId, 8)
      write(sUA, 9)
    }
  }

  override fun readFrom(ins: TarsInputStream) {
    ins.apply {
      lUid = read(lUid, 0, false)
      bAnonymous = read(bAnonymous, 1, false)
      sGuid = read(sGuid, 2, false)
      sToken = read(sToken, 3, false)
      lTid = read(lTid, 4, false)
      lSid = read(lSid, 5, false)
      lGroupId = read(lGroupId, 6, false)
      lGroupType = read(lGroupType, 7, false)
      sAppId = read(sAppId, 8, false)
      sUA = read(sUA, 9, false)
    }
  }

  override fun newInit(): TarsStructBase = this.copy()

}