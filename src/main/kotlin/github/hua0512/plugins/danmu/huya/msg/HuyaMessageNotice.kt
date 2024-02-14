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
 * @author hua0512
 * @date : 2024/2/10 19:44
 */
data class HuyaMessageNotice(
  var senderInfo: HuyaSenderInfo = HuyaSenderInfo(),
  var lTid: Long = 0,
  var lSid: Long = 0,
  var sContent: String = "",
  var iShowMode: Int = 0,
  var tFormat: HuyaContentFormat = HuyaContentFormat(),
  var tBulletFormat: HuyaBulletFormat = HuyaBulletFormat(),
  var iTermType: Int = 0,
  var vDecorationPrefix: List<HuyaDecorationInfo> = emptyList(),
  var vDecorationSuffix: List<HuyaDecorationInfo> = emptyList(),
  var vAtSomeone: List<HuyaUidNickName> = emptyList(),
  var lPid: Long = 0,
  var vBullletPrefix: List<HuyaDecorationInfo> = emptyList(),
  var sIconUrl: String = "",
  var iType: Int = 0,
  var vBulletSuffix: List<HuyaDecorationInfo> = emptyList(),

  ) : TarsStructBase() {


  //  private val vTagInfo: List<MessageTagInfo> = CollUtil.newArrayList(MessageTagInfo())
//  private val tSenceFormat: SendMessageFormat = SendMessageFormat()
//  private val tContentExpand: MessageContentExpand = MessageContentExpand()
//  private val iMessageMode = 0
  override fun writeTo(os: TarsOutputStream) {
    with(os) {
      write(senderInfo, 0)
      write(lTid, 1)
      write(lSid, 2)
      write(sContent, 3)
      write(iShowMode, 4)
      write(tFormat, 5)
      write(tBulletFormat, 6)
      write(iTermType, 7)
      write(vDecorationPrefix, 8)
      write(vDecorationSuffix, 9)
      write(vAtSomeone, 10)
      write(lPid, 11)
      write(vBullletPrefix, 12)
      write(sIconUrl, 13)
      write(iType, 14)
      write(vBulletSuffix, 15)
    }
  }

  override fun readFrom(tis: TarsInputStream) {
    tis.also {
      senderInfo = it.directRead(senderInfo, 0, false) as HuyaSenderInfo
      lTid = it.read(lTid, 1, true)
      lSid = it.read(lSid, 2, true)
      sContent = it.read(sContent, 3, true)
      iShowMode = it.read(iShowMode, 4, true)
      tFormat = it.directRead(tFormat, 5, true) as HuyaContentFormat
      tBulletFormat = it.directRead(tBulletFormat, 6, true) as HuyaBulletFormat
      iTermType = it.read(iTermType, 7, true)
      vDecorationPrefix = it.readArray(vDecorationPrefix, 8, true)
      vDecorationSuffix = it.readArray(vDecorationSuffix, 9, true)
      vAtSomeone = it.readArray(vAtSomeone, 10, true)
      lPid = it.read(lPid, 11, true)
      vBullletPrefix = it.readArray(vBullletPrefix, 12, false)
      sIconUrl = it.read(sIconUrl, 13, false)
      iType = it.read(iType, 14, false)
      vBulletSuffix = it.readArray(vBulletSuffix, 15, false)
    }
  }


}
