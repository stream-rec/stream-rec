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
package github.hua0512.plugins.huya.danmu.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

data class HuyaLiveAppUAEx(
  var sIMEI: String? = "",
  var sAPN: String? = "",
  var sNetType: String? = "",
  var sDeviceId: String? = "",
  var sMId: String? = "",
) : TarsStructBase() {

  override fun writeTo(os: TarsOutputStream) {
    os.write(this.sIMEI, 1)
    os.write(this.sAPN, 2)
    os.write(this.sNetType, 3)
    os.write(this.sDeviceId, 4)
    os.write(this.sMId, 5)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.sIMEI = `is`.read(this.sIMEI, 1, false)
    this.sAPN = `is`.read(this.sAPN, 2, false)
    this.sNetType = `is`.read(this.sNetType, 3, false)
    this.sDeviceId = `is`.read(this.sDeviceId, 4, false)
    this.sMId = `is`.read(this.sMId, 5, false)
  }
}