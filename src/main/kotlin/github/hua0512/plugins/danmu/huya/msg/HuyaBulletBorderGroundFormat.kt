package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

data class HuyaBulletBorderGroundFormat(
  var iEnableUse: Int = 0,
  var iBorderThickness: Int = 0,
  var iBorderColour: Int = -1,
  var iBorderDiaphaneity: Int = 100,
  var iGroundColour: Int = -1,
  var iGroundColourDiaphaneity: Int = 100,
  var sAvatarDecorationUrl: String = "",
  var iFontColor: Int = -1,
  var iTerminalFlag: Int = -1,
) : TarsStructBase() {
  override fun writeTo(os: TarsOutputStream) {
    os.write(this.iEnableUse, 0)
    os.write(this.iBorderThickness, 1)
    os.write(this.iBorderColour, 2)
    os.write(this.iBorderDiaphaneity, 3)
    os.write(this.iGroundColour, 4)
    os.write(this.iGroundColourDiaphaneity, 5)
    os.write(this.sAvatarDecorationUrl, 6)
    os.write(this.iFontColor, 7)
    os.write(this.iTerminalFlag, 8)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.iEnableUse = `is`.read(this.iEnableUse, 0, false)
    this.iBorderThickness = `is`.read(this.iBorderThickness, 1, false)
    this.iBorderColour = `is`.read(this.iBorderColour, 2, false)
    this.iBorderDiaphaneity = `is`.read(this.iBorderDiaphaneity, 3, false)
    this.iGroundColour = `is`.read(this.iGroundColour, 4, false)
    this.iGroundColourDiaphaneity = `is`.read(this.iGroundColourDiaphaneity, 5, false)
    this.sAvatarDecorationUrl = `is`.read(this.sAvatarDecorationUrl, 6, false)
    this.iFontColor = `is`.read(this.iFontColor, 7, false)
    this.iTerminalFlag = `is`.read(this.iTerminalFlag, 8, false)
  }

  override fun newInit(): TarsStructBase = this
}