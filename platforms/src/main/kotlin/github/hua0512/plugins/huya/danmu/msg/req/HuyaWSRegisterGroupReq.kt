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
import java.util.ArrayList

/**
 * Huya websocket register group req
 *
 * Extracted from https://hd2.huya.com/fedbasic/huyabaselibs/taf-signal/taf-signal.global.0.0.12.prod.js
 * @author hua0512
 * @date : 2024/2/10 18:28
 */
internal data class HuyaWSRegisterGroupReq(
  var vGroupId: List<String?> = ArrayList(),
  var sToken: String? = "",
) : TarsStructBase() {

  override fun writeTo(os: TarsOutputStream) {
    os.write(this.vGroupId, 0)
    os.write(this.sToken, 1)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.vGroupId = `is`.readArray(this.vGroupId, 0, false)
    this.sToken = `is`.read(this.sToken, 1, false)
  }
}