package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream

/**
 * @author hua0512
 * @date : 2024/2/10 19:29
 */
data class HuyaPushMessage(
  var ePushType: Int = 0,
  var dataBytes: ByteArray = byteArrayOf(),
  var iProtocolType: Int = 0,
  var sGroupId: String = "",
  var lMsgId: Long = 0,
  var iMsgTag: Int = 0,
) : HuyaBaseCommandMsg() {


  override fun writeTo(os: TarsOutputStream) {
    with(os) {
      write(ePushType, 0)
      write(lUri, 1)
      write(dataBytes, 2)
      write(iProtocolType, 3)
      write(sGroupId, 4)
      write(lMsgId, 5)
      write(iMsgTag, 6)
    }
  }

  override fun readFrom(`is`: TarsInputStream) {
    `is`.also {
      this.ePushType = it.read(this.ePushType, 0, true)
      this.lUri = it.read(this.lUri, 1, true)
      this.dataBytes = it.read(this.dataBytes, 2, true)
      this.iProtocolType = it.read(this.iProtocolType, 3, true)
      this.sGroupId = it.read(this.sGroupId, 4, true)
      this.lMsgId = it.read(this.lMsgId, 5, true)
      this.iMsgTag = it.read(this.iMsgTag, 6, true)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HuyaPushMessage

    if (ePushType != other.ePushType) return false
    if (!dataBytes.contentEquals(other.dataBytes)) return false
    if (iProtocolType != other.iProtocolType) return false
    if (sGroupId != other.sGroupId) return false
    if (lMsgId != other.lMsgId) return false
    if (iMsgTag != other.iMsgTag) return false

    return true
  }

  override fun hashCode(): Int {
    var result = ePushType
    result = 31 * result + dataBytes.contentHashCode()
    result = 31 * result + iProtocolType
    result = 31 * result + sGroupId.hashCode()
    result = 31 * result + lMsgId.hashCode()
    result = 31 * result + iMsgTag
    return result
  }
}