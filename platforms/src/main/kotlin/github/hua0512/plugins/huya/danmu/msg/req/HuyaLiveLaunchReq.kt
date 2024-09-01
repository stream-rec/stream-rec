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
import github.hua0512.plugins.huya.danmu.msg.HuyaLiveUserbase

/**
 * Huya danmu live launch info request
 * Extracted from https://hd2.huya.com/fedbasic/huyabaselibs/taf-signal/taf-signal.global.0.0.12.prod.js
 * @author hua0512
 * @date : 2024/8/17 14:25
 */
data class HuyaLiveLaunchReq(
  var tId: HuyaUserInfo = HuyaUserInfo(),
  var tLiveUB: HuyaLiveUserbase = HuyaLiveUserbase(),
  var bSupportDomain: Boolean = false,
) : TarsStructBase() {

  override fun writeTo(os: TarsOutputStream) {
    os.write(this.tId, 0)
    os.write(this.tLiveUB, 1)
    os.write(this.bSupportDomain, 2)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.tId = `is`.directRead(this.tId, 0, false) as HuyaUserInfo
    this.tLiveUB = `is`.directRead(this.tLiveUB, 1, false) as HuyaLiveUserbase
    this.bSupportDomain = `is`.read(this.bSupportDomain, 2, false)
  }
}