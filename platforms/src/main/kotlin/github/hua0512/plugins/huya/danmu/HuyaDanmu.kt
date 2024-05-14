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

package github.hua0512.plugins.huya.danmu

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import github.hua0512.app.App
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.media.DanmuDataWrapper.DanmuData
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.base.Danmu
import github.hua0512.plugins.huya.danmu.msg.HuyaMessageNotice
import github.hua0512.plugins.huya.danmu.msg.HuyaPushMessage
import github.hua0512.plugins.huya.danmu.msg.HuyaSocketCommand
import github.hua0512.plugins.huya.danmu.msg.HuyaUserInfo
import github.hua0512.plugins.huya.danmu.msg.data.HuyaCmds
import github.hua0512.plugins.huya.danmu.msg.data.HuyaOperations
import io.ktor.websocket.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.charset.StandardCharsets
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Huya danmu downloader
 * @author hua0512
 * @date : 2024/2/9 13:44
 */
class HuyaDanmu(app: App) : Danmu(app, enablePing = false) {

  override var websocketUrl: String = "wss://cdnws.api.huya.com:443"
  override val heartBeatDelay: Long = 60.toDuration(DurationUnit.SECONDS).inWholeMilliseconds

  // @formatter:off
  override val heartBeatPack: ByteArray = byteArrayOf(0x00, 0x03, 0x1d, 0x00, 0x00, 0x69, 0x00, 0x00, 0x00, 0x69, 0x10, 0x03, 0x2c, 0x3c, 0x4c, 0x56, 0x08, 0x6f, 0x6e, 0x6c, 0x69, 0x6e, 0x65, 0x75, 0x69, 0x66, 0x0f, 0x4f, 0x6e, 0x55, 0x73, 0x65, 0x72, 0x48, 0x65, 0x61, 0x72, 0x74, 0x42, 0x65, 0x61, 0x74, 0x7d, 0x00, 0x00, 0x3c, 0x08, 0x00, 0x01, 0x06, 0x04, 0x74, 0x52, 0x65, 0x71, 0x1d, 0x00, 0x00, 0x2f, 0x0a, 0x0a, 0x0c, 0x16, 0x00, 0x26, 0x00, 0x36, 0x07, 0x61, 0x64, 0x72, 0x5f, 0x77, 0x61, 0x70, 0x46, 0x00, 0x0b, 0x12, 0x03, 0xae.toByte(), 0xf0.toByte(), 0x0f, 0x22, 0x03, 0xae.toByte(), 0xf0.toByte(), 0x0f, 0x3c, 0x42, 0x6d, 0x52, 0x02, 0x60, 0x5c, 0x60, 0x01, 0x7c, 0x82.toByte(), 0x00, 0x0b, 0xb0.toByte(), 0x1f, 0x9c.toByte(), 0xac.toByte(), 0x0b, 0x8c.toByte(), 0x98.toByte(), 0x0c, 0xa8.toByte(), 0x0c)
  // @formatter:on

  internal var ayyuid: Long = 0
  internal var topsid: Long = 0
  internal var subid: Long = 0


  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    // check if streamer url is empty
    if (streamer.url.isEmpty()) return false

    logger.info("(${streamer.name}) Huya room info: ayyuid=$ayyuid, topsid=$topsid, subid=$subid")
    if (ayyuid == 0L || topsid == 0L || subid == 0L) {
      logger.error("Failed to get huya room info: ayyuid=$ayyuid, topsid=$topsid, subid=$subid")
      return false
    }
    return true
  }

  override fun oneHello(): ByteArray {
    val userInfo = HuyaUserInfo(
      ayyuid,
      lTid = topsid,
      lSid = subid,
    )
    // create a TarsOutputStream and write userInfo to it
    val tarsOutputStream = TarsOutputStream().also {
      userInfo.writeTo(it)
    }

    return HuyaSocketCommand(
      iCmdType = HuyaOperations.EWSCmd_RegisterReq.code,
      vData = tarsOutputStream.toByteArray()
    ).run {
      TarsOutputStream().run {
        writeTo(this)
        toByteArray()
      }
    }
  }

  override suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?> {
    val huyaSocketCommand = HuyaSocketCommand().apply {
      readFrom(TarsInputStream(data))
    }
    when (val commandType = huyaSocketCommand.iCmdType) {
      HuyaOperations.EWSCmdS2C_MsgPushReq.code -> {
        val msg = TarsInputStream(huyaSocketCommand.vData).run {
          HuyaPushMessage().apply {
            readFrom(this@run)
          }
        }
        // check if the message is a notice
        // TODO : consider supporting more cmd types
        if (msg.lUri == HuyaCmds.MessageNotice.code) {
          val msgNotice = TarsInputStream(msg.dataBytes).run {
            setServerEncoding(StandardCharsets.UTF_8.name())
            HuyaMessageNotice().apply {
              readFrom(this@run)
            }
          }
          val content = msgNotice.sContent
          // FUCK, huya danmu has no time attribute!
          // this is inaccurate
          val time = if (huyaSocketCommand.lTime == 0L) Clock.System.now().toEpochMilliseconds() else huyaSocketCommand.lTime
          // huya danmu contains only one danmu
          return listOf(
            DanmuData(
              msgNotice.senderInfo.sNickName,
              color = msgNotice.tBulletFormat.iFontColor,
              content = content,
              fontSize = msgNotice.tBulletFormat.iFontSize,
              serverTime = time
            )
          )
        }
      }

      else -> {
        HuyaOperations.fromCode(commandType) ?: logger.debug("Received unknown huya command: $commandType")
      }
    }
    return emptyList()
  }


}