package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

data class HuyaUidNickName(
  var lUid: Long = 0,
  var sNickName: String = "",
) : TarsStructBase() {
  override fun writeTo(os: TarsOutputStream) {
    os.write(this.lUid, 0)
    os.write(this.sNickName, 1)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.lUid = `is`.read(this.lUid, 0, true)
    this.sNickName = `is`.read(this.sNickName, 1, true)
  }

  override fun newInit(): TarsStructBase = this
}