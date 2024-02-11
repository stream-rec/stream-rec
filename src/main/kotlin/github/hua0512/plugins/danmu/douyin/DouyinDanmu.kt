package github.hua0512.plugins.danmu.douyin

import github.hua0512.app.App
import github.hua0512.data.DanmuData
import github.hua0512.data.Streamer
import github.hua0512.plugins.base.Danmu

/**
 * @author hua0512
 * @date : 2024/2/9 13:48
 */
class DouyinDanmu(app: App) : Danmu(app) {
  override val websocketUrl: String = "wss://webcast5-ws-web-lf.douyin.com:443/webcast/im/push/v2/"
  override val heartBeatDelay: Long
    get() = TODO("Not yet implemented")
  override val heartBeatPack: ByteArray
    get() = TODO("Not yet implemented")

  override suspend fun init(streamer: Streamer, startTime: Long): Boolean {
    TODO("Not yet implemented")
  }

  override fun oneHello(): ByteArray {
    TODO("Not yet implemented")
  }

  override fun decodeDanmu(data: ByteArray): DanmuData? {
    return null
  }
}