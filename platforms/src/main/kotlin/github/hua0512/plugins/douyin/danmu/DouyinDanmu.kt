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

package github.hua0512.plugins.douyin.danmu

import com.google.protobuf.ByteString
import douyin.Dy
import douyin.Dy.PushFrame
import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig.DouyinDownloadConfig
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.media.DanmuDataWrapper.DanmuData
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.douyin.download.DouyinExtractor
import github.hua0512.plugins.douyin.download.DouyinExtractor.Companion.commonDouyinParams
import github.hua0512.plugins.douyin.download.DouyinExtractor.Companion.extractDouyinRoomId
import github.hua0512.plugins.douyin.download.DouyinExtractor.Companion.populateDouyinCookieMissedParams
import github.hua0512.plugins.douyin.download.getSignature
import github.hua0512.plugins.douyin.download.loadWebmssdk
import github.hua0512.plugins.download.COMMON_HEADERS
import github.hua0512.utils.decompressGzip
import github.hua0512.utils.nonEmptyOrNull
import github.hua0512.utils.withIOContext
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.datetime.Instant

/**
 * @author hua0512
 * @date : 2024/2/9 13:48
 */
class DouyinDanmu(app: App) : Danmu(app, enablePing = false) {

  override var websocketUrl: String = "wss://webcast5-ws-web-hl.douyin.com/webcast/im/push/v2/"

  override val heartBeatDelay: Long = 10000

  override val heartBeatPack: ByteArray = byteArrayOf()

  init {
    // load webmssdk js
    loadWebmssdk()
  }

  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    // get room id
    val roomId = extractDouyinRoomId(streamer.url) ?: return false

    val config: DouyinDownloadConfig = if (streamer.templateStreamer != null) {
      // try to get templates download config
      streamer.templateStreamer?.downloadConfig?.run {
        /**
         * template config uses basic config [DefaultDownloadConfig], build a new douyin config using global platform values
         */
        DouyinDownloadConfig(
          quality = app.config.douyinConfig.quality,
          cookies = app.config.douyinConfig.cookies,
        ).also {
          it.danmu = this.danmu
          it.maxBitRate = this.maxBitRate
          it.outputFileFormat = this.outputFileFormat
          it.outputFileName = this.outputFileName
          it.outputFolder = this.outputFolder
          it.onPartedDownload = this.onPartedDownload ?: emptyList()
          it.onStreamingFinished = this.onStreamingFinished ?: emptyList()
        }
      } ?: throw IllegalArgumentException("${streamer.name} has template streamer but no download config") // should not happen
    } else {
      streamer.downloadConfig as? DouyinDownloadConfig ?: DouyinDownloadConfig()
    }

    var cookies = (config.cookies?.nonEmptyOrNull() ?: app.config.douyinConfig.cookies)?.nonEmptyOrNull() ?: ""

    try {
      cookies = populateDouyinCookieMissedParams(cookies, app.client)
    } catch (e: Exception) {
      logger.error("Failed to populate douyin cookie missed params", e)
      return false
    }

    val response = app.client.get("https://live.douyin.com/webcast/room/web/enter/") {
      headers {
        COMMON_HEADERS.forEach { append(it.first, it.second) }
        append(HttpHeaders.Referrer, DouyinExtractor.LIVE_DOUYIN_URL)
        append(HttpHeaders.Cookie, cookies)
      }
      commonDouyinParams.forEach { (t, u) ->
        parameter(t, u)
      }
      parameter("web_rid", roomId)
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
    logger.info("(${streamer.name}) douyin room id_str: $trueRoomId")
    commonDouyinParams.forEach { (t, u) ->
      requestParams[t] = u
    }
    requestParams["room_id"] = trueRoomId.toString()
    requestParams["web_rid"] = roomId
    // generate user id if not exists
    requestParams["user_unique_id"] = DouyinExtractor.USER_ID!!
    val signature = getSignature(trueRoomId.toString(), DouyinExtractor.USER_ID)
    // add signature to request params
    requestParams["signature"] = signature
    headersMap[HttpHeaders.Cookie] = cookies
    return true
  }

  override fun launchHeartBeatJob(session: WebSocketSession) {
    // Douyin does not use heart beat
  }

  override fun oneHello(): ByteArray {
    // Douyin does not use hello
    return byteArrayOf()
  }

  override suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?> {
    val pushFrame = PushFrame.parseFrom(data)
    val logId = pushFrame.logId
    val payload = pushFrame.payload.toByteArray()
    val decompressed = withIOContext {
      decompressGzip(payload)
    }
    val payloadPackage = Dy.Response.parseFrom(decompressed)
    val internalExt = payloadPackage.internalExtBytes
    if (payloadPackage.needAck) {
      sendAck(session, logId, internalExt)
    }
    val msgList = payloadPackage.messagesListList
    // each frame may contain multiple messages
    return msgList.mapNotNull { msg ->
      when (msg.method) {
        "WebcastChatMessage" -> {
          val chatMessage = Dy.ChatMessage.parseFrom(msg.payload)
          val textColor = chatMessage.rtfContent.defaultFormat.color.run {
            if (this.isNullOrEmpty()) -1 else this.toInt(16)
          }
          DanmuData(
            chatMessage.user.nickNameBytes.toStringUtf8(),
            textColor,
            chatMessage.contentBytes.toStringUtf8(),
            chatMessage.rtfContent.defaultFormat.fontSize,
            chatMessage.eventTime * 1000,
          )
        }

        // TODO :support other types of messages
        else -> {
//          logger.info("DouyinDanmu: ${msg.method}")
          null
        }
      }
    }
  }


  private suspend fun sendAck(session: WebSocketSession, logId: Long, internalExt: ByteString) {
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