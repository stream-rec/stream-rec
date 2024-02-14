package github.hua0512.plugins.danmu.douyin

import com.google.protobuf.ByteString
import douyin.Dy
import douyin.Dy.PushFrame
import github.hua0512.app.App
import github.hua0512.data.DanmuData
import github.hua0512.data.Streamer
import github.hua0512.data.config.DouyinDownloadConfig
import github.hua0512.plugins.base.Danmu
import github.hua0512.plugins.base.Download
import github.hua0512.utils.commonDouyinParams
import github.hua0512.utils.decompressGzip
import github.hua0512.utils.extractDouyinRoomId
import github.hua0512.utils.getDouyinTTwid
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author hua0512
 * @date : 2024/2/9 13:48
 */
class DouyinDanmu(app: App) : Danmu(app) {

  override var websocketUrl: String = "wss://webcast3-ws-web-lf.douyin.com:443/webcast/im/push/v2/"

  override val heartBeatDelay: Long = 10

  override val heartBeatPack: ByteArray = byteArrayOf()

  override suspend fun init(streamer: Streamer, startTime: Long): Boolean {
    this.startTime = startTime
    // get room id
    val roomId = extractDouyinRoomId(streamer.url) ?: return false

    val config = streamer.downloadConfig as? DouyinDownloadConfig ?: DouyinDownloadConfig()

    val cookies = (config.cookies ?: app.config.douyinConfig.cookies).let {
      if (it.isNullOrEmpty()) {
        logger.error("Empty douyin cookies")
        return false
      }
      if ("ttwid" !in it) {
        val ttwid = app.client.getDouyinTTwid()
        if (ttwid.isEmpty()) {
          logger.error("Failed to get ttwid from cookies")
          return false
        }
        return@let "$it; ttwid=$ttwid"
      }
      it
    }

    val response = withContext(Dispatchers.IO) {
      app.client.get("https://live.douyin.com/webcast/room/web/enter/") {
        headers {
          Download.commonHeaders.forEach { append(it.first, it.second) }
          append(HttpHeaders.Referrer, "https://live.douyin.com")
          append(HttpHeaders.Cookie, cookies)
        }
        commonDouyinParams.forEach { t, u ->
          parameter(t, u)
        }
        parameter("web_rid", roomId)
      }
    }

    if (response.status != HttpStatusCode.OK) {
      logger.debug("Streamer : {} response status is not OK : {}", streamer.name, response.status)
      return false
    }

    val data = response.bodyAsText()
    val roomIdPattern = "id_str\"\\s*:\\s*\"(\\d+)".toRegex()
    val trueRoomId = roomIdPattern.find(data)?.groupValues?.get(1)?.toLong() ?: 0
    if (trueRoomId == 0L) {
      logger.error("Failed to get douyin room id_str")
      return false
    }
    logger.info("(${streamer.name}) Douyin room id_str: $trueRoomId")
    commonDouyinParams.forEach { (t, u) ->
      requestParams[t] = u
    }
    requestParams["room_id"] = trueRoomId.toString()
    requestParams["web_rid"] = roomId
    headersMap[HttpHeaders.Cookie] = cookies
    isInitialized.set(true)
    return true
  }

  override fun launchHeartBeatJob(session: DefaultClientWebSocketSession) {
    // Douyin does not use heart beat
  }

  override fun oneHello(): ByteArray {
    // Douyin does not use hello
    return byteArrayOf()
  }

  override suspend fun decodeDanmu(session: DefaultClientWebSocketSession, data: ByteArray): DanmuData? {
    val pushFrame = PushFrame.parseFrom(data)
    val logId = pushFrame.logId
    val payload = pushFrame.payload.toByteArray()
    val decompressed = withContext(Dispatchers.IO) {
      decompressGzip(payload)
    }
    val payloadPackage = Dy.Response.parseFrom(decompressed)
    val internalExt = payloadPackage.internalExtBytes
    if (payloadPackage.needAck) {
      sendAck(session, logId, internalExt)
    }
    val msgList = payloadPackage.messagesListList
    msgList.map { msg ->
      when (msg.method) {
        "WebcastChatMessage" -> {
          val chatMessage = Dy.ChatMessage.parseFrom(msg.payload)
          val textColor = chatMessage.rtfContent.defaultFormat.color.run {
            if (this.isNullOrEmpty()) -1 else this.toInt(16)
          }
          return DanmuData(
            chatMessage.user.nickNameBytes.toStringUtf8(),
            textColor,
            chatMessage.contentBytes.toStringUtf8(),
            chatMessage.rtfContent.defaultFormat.fontSize,
            chatMessage.eventTime * 1000,
          ).also {
            logger.info("DouyinDanmu: $it, time : ${chatMessage.eventTime}")
          }
        }

        // TODO :support other types of messages
        else -> {
//          logger.info("DouyinDanmu: ${msg.method}")
        }
      }
    }
    return null
  }


  private suspend fun sendAck(session: DefaultClientWebSocketSession, logId: Long, internalExt: ByteString) {
//    logger.debug("Sending ack for logId: $logId")
    val pushFrame = PushFrame.newBuilder()
      .setPayloadType("ack")
      .setLogId(logId)
      .setPayload(internalExt)
      .build()
    val byteArray = pushFrame.toByteArray()
    session.send(byteArray)
  }
}