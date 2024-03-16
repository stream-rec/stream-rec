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

data class HuyaBulletFormat(
  var iFontColor: Int = -1,
  var iFontSize: Int = 4,
  var iTextSpeed: Int = 0,
  var iTransitionType: Int = 1,
  var iPopupStyle: Int = 0,
  var tBorderGroundFormat: HuyaBulletBorderGroundFormat = HuyaBulletBorderGroundFormat(),
  var vGraduatedColor: List<Int> = emptyList(),
  var iAvatarFlag: Int = 0,
  var iAvatarTerminalFlag: Int = -1,
) : TarsStructBase() {
  override fun writeTo(os: TarsOutputStream) {
    os.write(this.iFontColor, 0)
    os.write(this.iFontSize, 1)
    os.write(this.iTextSpeed, 2)
    os.write(this.iTransitionType, 3)
    os.write(this.iPopupStyle, 4)
    os.write(this.tBorderGroundFormat, 5)
    os.write(this.vGraduatedColor, 6)
    os.write(this.iAvatarFlag, 7)
    os.write(this.iAvatarTerminalFlag, 8)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.iFontColor = `is`.read(this.iFontColor, 0, false)
    this.iFontSize = `is`.read(this.iFontSize, 1, false)
    this.iTextSpeed = `is`.read(this.iTextSpeed, 2, false)
    this.iTransitionType = `is`.read(this.iTransitionType, 3, false)
    this.iPopupStyle = `is`.read(this.iPopupStyle, 4, false)
    this.tBorderGroundFormat = `is`.directRead(this.tBorderGroundFormat, 5, false) as HuyaBulletBorderGroundFormat
    this.vGraduatedColor = `is`.readArray(this.vGraduatedColor, 6, false)
    this.iAvatarFlag = `is`.read(this.iAvatarFlag, 7, false)
    this.iAvatarTerminalFlag = `is`.read(this.iAvatarTerminalFlag, 8, false)
  }

  override fun newInit(): TarsStructBase = this
}