package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

data class HuyaSenderInfo(
  var lUid: Long = 0,
  var lImid: Long = 0,
  var sNickName: String = "",
  var iGender: Int = 0,
  var sAvatarUrl: String? = null,
  var iNobleLevel: Int = 0,
  var tNobleLevelInfo: HuyaNobleLevelInfo = HuyaNobleLevelInfo(),
  var sGuid: String? = null,
  var sHuYaUA: String? = null,
) : TarsStructBase() {
  override fun writeTo(os: TarsOutputStream) {
    os.write(this.lUid, 0)
    os.write(this.lImid, 1)
    os.write(this.sNickName, 2)
    os.write(this.iGender, 3)
    os.write(this.sAvatarUrl, 4)
    os.write(this.iNobleLevel, 5)
    os.write(this.tNobleLevelInfo, 6)
    os.write(this.sGuid, 7)
    os.write(this.sHuYaUA, 8)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.lUid = `is`.read(this.lUid, 0, true)
    this.lImid = `is`.read(this.lImid, 1, true)
    this.sNickName = `is`.read(this.sNickName, 2, true)
    this.iGender = `is`.read(this.iGender, 3, true)
    this.sAvatarUrl = `is`.read(this.sAvatarUrl, 4, true)
    this.iNobleLevel = `is`.read(this.iNobleLevel, 5, true)
    this.tNobleLevelInfo = `is`.directRead(this.tNobleLevelInfo, 6, true) as HuyaNobleLevelInfo
    this.sGuid = `is`.read(this.sGuid, 7, true)
    this.sHuYaUA = `is`.read(this.sHuYaUA, 8, true)
  }

  override fun newInit(): TarsStructBase {
    return this
  }
}