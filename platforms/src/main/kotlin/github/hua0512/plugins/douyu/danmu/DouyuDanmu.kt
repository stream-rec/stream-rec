/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.plugins.douyu.danmu

import github.hua0512.app.App
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.douyu.download.DouyuExtractor
import github.hua0512.plugins.douyu.download.extractDouyunRidFromUrl
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Douyu danmu client implementation
 * Built based on: https://open.douyu.com/source/api/63
 * @param app application instance
 * @author hua0512
 * @date : 2024/3/23 0:08
 */
open class DouyuDanmu(app: App) : Danmu(app, enablePing = false) {
  // web socket url, ports available from 8501 to 8506, both included
  override var websocketUrl = "wss://danmuproxy.douyu.com:8502/"

  // heart beat delay, 45s according to official doc
  override val heartBeatDelay: Long = 45000

  // heart beat is a stt message: type@=mrkl/
  override val heartBeatPack: ByteArray = DouyuPacket.encode(DouyuSTT.serialize(mapOf("type" to "mrkl")))

  /**
   * Douyu room id
   */
  internal lateinit var rid: String

  init {
    headersMap[HttpHeaders.Origin] = DouyuExtractor.DOUYU_URL
    headersMap[HttpHeaders.Referrer] = DouyuExtractor.DOUYU_URL
  }

  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    if (rid.isEmpty()) {
      // try to get rid from url
      extractDouyunRidFromUrl(streamer.url)?.let {
        rid = it
        return true
      }
      // extract rid from html
      val response = app.client.get(streamer.url)
      if (response.status.value != 200) {
        logger.error("Failed to get douyu room id: ${response.status}")
        return false
      }
      val matchResult = DouyuExtractor.midPatterns.firstNotNullOfOrNull { it.toRegex().find(response.bodyAsText()) }
      if (matchResult == null) {
        logger.error("Failed to get douyu room id")
        return false
      }
      rid = matchResult.groupValues[1]
    }
    return true
  }

  override fun oneHello(): ByteArray {
    // loginreq and joingroup message
    val loginPacket = DouyuPacket.encode(DouyuSTT.serialize(mapOf("type" to "loginreq", "roomid" to rid)))
    val joinGroup = DouyuPacket.encode(DouyuSTT.serialize(mapOf("type" to "joingroup", "rid" to rid, "gid" to "-9999")))
    return loginPacket + joinGroup
  }

  override fun onDanmuRetry(retryCount: Int) {
    // do nothing
  }

  override suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?> {
    val messages = DouyuPacket.decode(data)
    val danmus = mutableListOf<DanmuDataWrapper>()
    messages.forEach {
      // filter chat message
      if (it.startsWith("type@=chatmsg")) {
        // {type=chatmsg, rid=8984762, ct=3, uid=372185194, nn=EQ118, txt=感谢 20岁天才鲨手老王 送出的 航海礼包 50个, cid=7491cce1460d411c7728540000000000, ic=avatar_v3/202301/fa1c37bafa33404aa16a7862c60f0b19, level=45, sahf=0, nl=3, rg=5, bnn=, bl=0, brid=0, hc=, ol=76, lk=, dms=4, pdg=38, pdk=1, ext=}
        /***
         * 弹幕消息
         * 用户在房间发送弹幕时,服务端发此消息给客户端,完整的数据部分应包含的字 段如下:
         *
         * 字段说明
         * type	表示为“弹幕”消息，固定为 chatmsg
         * rid	房间 id
         * hashid	发送者 uid
         * nn	发送者昵称
         * txt	弹幕文本内容
         * level	用户等级
         * gt	礼物头衔：默认值 0（表示没有头衔）
         * col	颜色：默认值 0（表示默认颜色弹幕）
         * ct	客户端类型：默认值 0
         * rg	房间权限组：默认值 1（表示普通权限用户）
         * pg	平台权限组：默认值 1（表示普通权限用户）
         * dlv	酬勤等级：默认值 0（表示没有酬勤）
         * dc	酬勤数量：默认值 0（表示没有酬勤数量）
         * bdlv	最高酬勤等级：默认值 0（表示全站都没有酬勤）
         * cmt	弹幕具体类型: 默认值 0（普通弹幕）
         * sahf	扩展字段，一般不使用，可忽略
         * ic	用户头像
         * nl	贵族等级
         * nc	贵族弹幕标识,0-非贵族弹幕,1-贵族弹幕,默认值 0
         * gatin	进入网关服务时间戳
         * chtin	进入房间服务时间戳
         * repin	进入发送服务时间戳
         * bnn	徽章昵称
         * bl	徽章等级
         * brid	徽章房间 id
         * hc	徽章信息校验码
         * ol	主播等级
         * rev	是否反向弹幕标记: 0-普通弹幕，1-反向弹幕, 默认值 0
         * hl	是否高亮弹幕标记: 0-普通，1-高亮, 默认值 0
         * ifs	是否粉丝弹幕标记: 0-非粉丝弹幕，1-粉丝弹幕, 默认值 0
         * p2p	服务功能字段
         * el	用户获得的连击特效：数组类型，数组包含 eid（特效 id）,etp（特效类型）,sc（特效次数）信息，ef（特效标志）。
         */
        val decoded = DouyuSTT.deserialize(it)
        val danmu = if (decoded is Map<*, *>) {
          DanmuDataWrapper.DanmuData(
            uid = (decoded["uid"] ?: decoded["hashid"]).toString().toLong(),
            sender = decoded["nn"] as String,
            color = 0,
            content = decoded["txt"] as String,
            fontSize = 0,
            serverTime = Clock.System.now().toEpochMilliseconds(),
          )
        } else {
          // decode danmu using regex
          val decodedString = it.toString()
          DanmuDataWrapper.DanmuData(
            uid = decodedString.substringAfter("uid=").substringBefore(",").toLong(),
            sender = decodedString.substringAfter("nn=").substringBefore(","),
            color = 0,
            content = decodedString.substringAfter("txt=").substringBefore(","),
            fontSize = 0,
            serverTime = Clock.System.now().toEpochMilliseconds(),
          )
        }
        danmus.add(danmu)
      }
    }
    return danmus
  }
}