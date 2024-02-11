package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase

data class HuyaDecorationInfo(
  var iAppId: Int = 0,
  var iViewType: Int = 0,
  var vData: ByteArray = byteArrayOf(),
) : TarsStructBase() {

  override fun writeTo(os: TarsOutputStream) {
    os.write(this.iAppId, 0)
    os.write(this.iViewType, 1)
    os.write(this.vData, 2)
  }

  override fun readFrom(`is`: TarsInputStream) {
    this.iAppId = `is`.read(this.iAppId, 0, true)
    this.iViewType = `is`.read(this.iViewType, 1, true)
    this.vData = `is`.read(this.vData, 2, true)
  }

  override fun newInit(): TarsStructBase = this
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HuyaDecorationInfo

    if (iAppId != other.iAppId) return false
    if (iViewType != other.iViewType) return false
    if (!vData.contentEquals(other.vData)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = iAppId
    result = 31 * result + iViewType
    result = 31 * result + vData.contentHashCode()
    return result
  }
}