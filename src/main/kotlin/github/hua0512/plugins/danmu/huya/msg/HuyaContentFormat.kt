package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

data class HuyaContentFormat(
  var iFontColor: Int = -1,
  var iFontSize: Int = 4,
  var iPopupStyle: Int = 0,
  var iNickNameFontColor: Int = -1,
  var iDarkFontColor: Int = -1,
  var iDarkNickNameFontColor: Int = -1,
) : TarsStructBase() {

  override fun writeTo(os: TarsOutputStream) {
    os.write(this.iFontColor, 0)
    os.write(this.iFontSize, 1)
    os.write(this.iPopupStyle, 2)
    os.write(this.iNickNameFontColor, 3)
    os.write(this.iDarkFontColor, 4)
    os.write(this.iDarkNickNameFontColor, 5)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.iFontColor = `is`.read(this.iFontColor, 0, false)
    this.iFontSize = `is`.read(this.iFontSize, 1, false)
    this.iPopupStyle = `is`.read(this.iPopupStyle, 2, false)
    this.iNickNameFontColor = `is`.read(this.iNickNameFontColor, 3, false)
    this.iDarkFontColor = `is`.read(this.iDarkFontColor, 4, false)
    this.iDarkNickNameFontColor = `is`.read(this.iDarkNickNameFontColor, 5, false)
  }

  override fun newInit(): TarsStructBase = this
}