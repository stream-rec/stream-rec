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

/**
 * @author hua0512
 * @date : 2024/2/10 19:02
 */
data class HuyaSocketCommand(
  var iCmdType: Int = 0,
  var vData: ByteArray = byteArrayOf(),
  var lRequestId: Long = 0,
  var traceId: String = "",
  var iEncryptType: Int = 0,
  var lTime: Long = 0,
  var sMD5: String = "",
) : TarsStructBase() {

  override fun readFrom(tis: TarsInputStream) {
    tis.also {
      iCmdType = it.read(iCmdType, 0, false)
      vData = it.read(vData, 1, false)
      lRequestId = it.read(lRequestId, 2, false)
      traceId = it.read(traceId, 3, false)
      iEncryptType = it.read(iEncryptType, 4, false)
      lTime = it.read(lTime, 5, false)
      sMD5 = it.read(sMD5, 6, false)
    }
  }

  override fun writeTo(tos: TarsOutputStream) {
    with(tos) {
      this.write(iCmdType, 0)
      this.write(vData, 1)
      this.write(lRequestId, 2)
      this.write(traceId, 3)
      this.write(iEncryptType, 4)
      this.write(lTime, 5)
      this.write(sMD5, 6)
    }
  }

  override fun newInit(): TarsStructBase = this.copy()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HuyaSocketCommand

    if (iCmdType != other.iCmdType) return false
    if (!vData.contentEquals(other.vData)) return false
    if (lRequestId != other.lRequestId) return false
    if (traceId != other.traceId) return false
    if (iEncryptType != other.iEncryptType) return false
    if (lTime != other.lTime) return false
    if (sMD5 != other.sMD5) return false

    return true
  }

  override fun hashCode(): Int {
    var result = iCmdType
    result = 31 * result + vData.contentHashCode()
    result = 31 * result + lRequestId.hashCode()
    result = 31 * result + traceId.hashCode()
    result = 31 * result + iEncryptType
    result = 31 * result + lTime.hashCode()
    result = 31 * result + sMD5.hashCode()
    return result
  }
}