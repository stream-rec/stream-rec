package github.hua0512.plugins.danmu.huya.msg

import com.qq.tars.protocol.tars.TarsStructBase

/**
 * @author hua0512
 * @date : 2024/2/10 19:32
 */
abstract class HuyaBaseCommandMsg : TarsStructBase() {

  var lUri: Long = 0
}