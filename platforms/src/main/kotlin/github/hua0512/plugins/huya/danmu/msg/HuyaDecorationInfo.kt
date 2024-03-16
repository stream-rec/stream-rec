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

data class HuyaDecorationInfo(
  var iAppId: Int = 0,
  var iViewType: Int = 0,
  var vData: ByteArray = byteArrayOf(),
) : TarsStructBase() {

  override fun writeTo(os: TarsOutputStream) {
    os.write(this.iAppId, 0)
    os.write(this.iViewType, 1)
    os.write(this.vData, 2)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.iAppId = `is`.read(this.iAppId, 0, true)
    this.iViewType = `is`.read(this.iViewType, 1, true)
    this.vData = `is`.read(this.vData, 2, true)
  }

  override fun newInit(): TarsStructBase = this
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HuyaDecorationInfo

    if (iAppId != other.iAppId) return false
    if (iViewType != other.iViewType) return false
    if (!vData.contentEquals(other.vData)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = iAppId
    result = 31 * result + iViewType
    result = 31 * result + vData.contentHashCode()
    return result
  }
}