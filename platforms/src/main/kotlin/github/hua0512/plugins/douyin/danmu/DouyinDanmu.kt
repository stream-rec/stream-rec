/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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
import github.hua0512.data.media.DanmuDataWrapper.EndOfDanmu
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.douyin.danmu.DouyinWebcastMessages.CHAT_MESSAGE
import github.hua0512.plugins.douyin.danmu.DouyinWebcastMessages.CONTROL_MESSAGE
import github.hua0512.plugins.douyin.download.*
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.ROOM_ID_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.SIGNATURE_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.USER_UNIQUE_KEY
import github.hua0512.utils.decompressGzip
import github.hua0512.utils.nonEmptyOrNull
import github.hua0512.utils.withIOContext
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.datetime.Instant


/**
 * Douyin danmu client
 * @author hua0512
 * @date : 2024/2/9 13:48
 */
open class DouyinDanmu(app: App) : Danmu(app, enablePing = false) {

  companion object {
    init {
      // load webmssdk js
      loadWebmssdk()
    }
  }


  override var websocketUrl: String = DouyinApi.randomWebSocketUrl

  override val heartBeatDelay: Long = 15000

  override val heartBeatPack: ByteArray = run {
    val heartbeatPack = Dy.PushFrame.newBuilder()
      .setPayloadType("hb")
      .build()
    heartbeatPack.toByteArray()
  }

  private var userUniqueId: String? = null

  internal var idStr = ""

  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    // get room id
    val webRid = extractDouyinWebRid(streamer.url) ?: return false

    val config: DouyinDownloadConfig = streamer.downloadConfig as DouyinDownloadConfig

    var cookies = (config.cookies?.nonEmptyOrNull() ?: app.config.douyinConfig.cookies)?.nonEmptyOrNull() ?: ""

    try {
      cookies = populateDouyinCookieMissedParams(cookies, app.client)
    } catch (e: Exception) {
      logger.error("{} Failed to populate douyin cookie missed params", webRid, e)
      return false
    }

    if (idStr.isEmpty()) {
      logger.error("{} Failed to get douyin room id_str", webRid)
      return false
    }
    logger.info("${streamer.name} douyin room id_str: $idStr")

    with(requestParams) {
      fillDouyinCommonParams()
      fillDouyinWsParams()

      this[ROOM_ID_KEY] = idStr
      updateSignature()
    }
    headersMap[HttpHeaders.Cookie] = cookies
    return true
  }

  override fun onDanmuRetry(retryCount: Int) {
    // update signature
    updateSignature()
  }

  private fun updateSignature() {
    // update ws url
    websocketUrl = DouyinApi.randomWebSocketUrl
    assert(requestParams[ROOM_ID_KEY] != null) { "$ROOM_ID_KEY is null" }
    // user unique id may be expired, get a new one
    userUniqueId = getValidUserId().toString()
    requestParams[USER_UNIQUE_KEY] = userUniqueId!!
    // update signature
    val signatureResult = getSignature(requestParams[ROOM_ID_KEY]!!, userUniqueId!!)
    if (signatureResult.isErr) {
      logger.error("{} Failed to get douyin signature: {}", idStr, signatureResult.error)
      return
    }
    requestParams[SIGNATURE_KEY] = signatureResult.value
  }

  override fun oneHello(): ByteArray {
    // Douyin does not use hello
    return byteArrayOf()
  }

  override suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?> {
    val pushFrame = PushFrame.parseFrom(data)
    val logId = pushFrame.logId

    // flag to indicate whether the payload is compressed (gzipped)
    var isGzipped = pushFrame.headersListList.find { it.key == "compress_type" }?.let {
      it.value == "gzip"
    } == true

    // payload may be compressed or not
    val payload = pushFrame.payload.toByteArray()
    // decompress payload, may be gzip or not
    val decompressed = if (isGzipped) {
      try {
        withIOContext {
          decompressGzip(payload)
        }
      } catch (e: Exception) {
        logger.error("douyin: failed to decompress payload: $pushFrame", e)
        return emptyList()
      }
    } else {
      payload
    }

    val payloadPackage = Dy.Response.parseFrom(decompressed)
    val internalExt = payloadPackage.internalExtBytes
    if (payloadPackage.needAck) {
      sendAck(session, logId, internalExt)
    }
    val msgList = payloadPackage.messagesListList
    // each frame may contain multiple messages
    return msgList.mapNotNull { msg ->
      logger.trace("msg: {}", msg)
      val msgType = DouyinWebcastMessages.fromClassName(msg.method)
      when (msgType) {
        CHAT_MESSAGE -> {
          val chatMessage = Dy.ChatMessage.parseFrom(msg.payload)
          val textColor = chatMessage.rtfContent.defaultFormat.color.run {
            if (this.isNullOrEmpty()) -1 else this.toInt(16)
          }
          val time = if (chatMessage.eventTime == 0L) System.currentTimeMillis() else chatMessage.eventTime * 1000
          DanmuData(
            chatMessage.user.id,
            chatMessage.user.nickNameBytes.toStringUtf8(),
            textColor,
            chatMessage.contentBytes.toStringUtf8(),
            chatMessage.rtfContent.defaultFormat.fontSize,
            time,
          )
        }

        CONTROL_MESSAGE -> {
          val controlMessage = Dy.ControlMessage.parseFrom(msg.payload)
          val status = controlMessage.status
          if (status == 3) {
            EndOfDanmu
          } else null
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
    val pushFrame = PushFrame.newBuilder()
      .setPayloadType("ack")
      .setLogId(logId)
      .setPayload(internalExt)
      .build()
    val byteArray = pushFrame.toByteArray()
    session.send(byteArray)
    logger.trace("sent ack : {}", byteArray.decodeToString())
  }
}