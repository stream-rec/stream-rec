package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

/**
 * Huya websocket user info
 * @author hua0512
 * @date : 2024/2/10 18:26
 */
data class HuyaUserInfo(
  var lUid: Long = 0,
  var bAnonymous: Boolean = true,
  var sGuid: String = "",
  var sToken: String = "",
  var lTid: Long = 0,
  var lSid: Long = 0,
  var lGroupId: Long = 0,
  var lGroupType: Long = 0,
  var sAppId: String = "",
  var sUA: String = "",
) : TarsStructBase() {
  override fun writeTo(os: TarsOutputStream) {
    with(os) {
      write(lUid, 0)
      write(bAnonymous, 1)
      write(sGuid, 2)
      write(sToken, 3)
      write(lTid, 4)
      write(lSid, 5)
      write(lGroupId, 6)
      write(lGroupType, 7)
      write(sAppId, 8)
      write(sUA, 9)
    }
  }

  override fun readFrom(ins: TarsInputStream) {
    ins.apply {
      lUid = read(lUid, 0, false)
      bAnonymous = read(bAnonymous, 1, false)
      sGuid = read(sGuid, 2, false)
      sToken = read(sToken, 3, false)
      lTid = read(lTid, 4, false)
      lSid = read(lSid, 5, false)
      lGroupId = read(lGroupId, 6, false)
      lGroupType = read(lGroupType, 7, false)
      sAppId = read(sAppId, 8, false)
      sUA = read(sUA, 9, false)
    }
  }

  override fun newInit(): TarsStructBase = this.copy()

}