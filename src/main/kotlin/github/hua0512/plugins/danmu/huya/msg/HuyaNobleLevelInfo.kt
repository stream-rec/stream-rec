package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

data class HuyaNobleLevelInfo(
  var iNobleLevel: Int = 0,
  var iAttrType: Int = 0,
) : TarsStructBase() {
  override fun writeTo(os: TarsOutputStream) {
    os.write(this.iNobleLevel, 0)
    os.write(this.iAttrType, 1)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.iNobleLevel = `is`.read(this.iNobleLevel, 0, true)
    this.iAttrType = `is`.read(this.iAttrType, 1, true)
  }

  override fun newInit(): TarsStructBase {
    return this
  }
}