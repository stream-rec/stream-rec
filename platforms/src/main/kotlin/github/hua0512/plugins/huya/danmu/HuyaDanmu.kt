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
import github.hua0512.app.App
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.media.DanmuDataWrapper.DanmuData
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.huya.danmu.msg.HuyaMessageNotice
import github.hua0512.plugins.huya.danmu.msg.HuyaPushMessage
import github.hua0512.plugins.huya.danmu.msg.HuyaSocketCommand
import github.hua0512.plugins.huya.danmu.msg.data.HuyaCmds
import github.hua0512.plugins.huya.danmu.msg.data.HuyaOperations
import github.hua0512.plugins.huya.danmu.msg.data.HuyaSourceType
import github.hua0512.plugins.huya.danmu.msg.req.HuyaGetLivingInfoReq
import github.hua0512.plugins.huya.danmu.msg.req.HuyaLiveLaunchReq
import github.hua0512.plugins.huya.danmu.msg.req.HuyaUserInfo
import github.hua0512.plugins.huya.danmu.msg.req.HuyaWSRegisterGroupReq
import github.hua0512.plugins.huya.danmu.msg.req.HuyaWup
import io.ktor.websocket.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Huya danmu downloader
 * @author hua0512
 * @date : 2024/2/9 13:44
 */
class HuyaDanmu(app: App) : Danmu(app, enablePing = false) {

  override var websocketUrl: String = HUYA_WS_URL
  override val heartBeatDelay: Long = 60.toDuration(DurationUnit.SECONDS).inWholeMilliseconds

  // @formatter:off
  override val heartBeatPack: ByteArray = byteArrayOf(0x00, 0x03, 0x1d, 0x00, 0x00, 0x69, 0x00, 0x00, 0x00, 0x69, 0x10, 0x03, 0x2c, 0x3c, 0x4c, 0x56, 0x08, 0x6f, 0x6e, 0x6c, 0x69, 0x6e, 0x65, 0x75, 0x69, 0x66, 0x0f, 0x4f, 0x6e, 0x55, 0x73, 0x65, 0x72, 0x48, 0x65, 0x61, 0x72, 0x74, 0x42, 0x65, 0x61, 0x74, 0x7d, 0x00, 0x00, 0x3c, 0x08, 0x00, 0x01, 0x06, 0x04, 0x74, 0x52, 0x65, 0x71, 0x1d, 0x00, 0x00, 0x2f, 0x0a, 0x0a, 0x0c, 0x16, 0x00, 0x26, 0x00, 0x36, 0x07, 0x61, 0x64, 0x72, 0x5f, 0x77, 0x61, 0x70, 0x46, 0x00, 0x0b, 0x12, 0x03, 0xae.toByte(), 0xf0.toByte(), 0x0f, 0x22, 0x03, 0xae.toByte(), 0xf0.toByte(), 0x0f, 0x3c, 0x42, 0x6d, 0x52, 0x02, 0x60, 0x5c, 0x60, 0x01, 0x7c, 0x82.toByte(), 0x00, 0x0b, 0xb0.toByte(), 0x1f, 0x9c.toByte(), 0xac.toByte(), 0x0b, 0x8c.toByte(), 0x98.toByte(), 0x0c, 0xa8.toByte(), 0x0c)
  // @formatter:on

  internal var presenterUid = 0L


  private companion object {
    private const val HUYA_WS_URL = "wss://cdnws.api.huya.com:443"
  }


  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    // check if streamer url is empty
    if (streamer.url.isEmpty()) return false

    logger.info("(${streamer.name}) Huya room info: presenterUid=${presenterUid}")
    if (presenterUid == 0L) {
      logger.error("Failed to get huya room info: presenterUid is 0")
      return false
    }
    return true
  }

  override fun oneHello(): ByteArray = ByteArray(0)

  override fun onDanmuRetry(retryCount: Int) {
    // do nothing
  }

  override suspend fun sendHello(session: WebSocketSession) {
    // format date to yyMMddHHmm
    // this is used as version number
    val date = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai"))
      .toJavaLocalDateTime()
      .format(DateTimeFormatter.ofPattern("yyMMddHHmm"))

    // lUid, sCookie, sGuid are not required
    // omitting them
    val userInfo = HuyaUserInfo(
      sUA = "webh5&$date&websocket",
      sDeviceInfo = "chrome",
    )

    // create a HuyaGetLivingInfoReq wup
    val command = HuyaSocketCommand(
      iCmdType = HuyaOperations.EWSCmd_WupReq.code,
      vData = HuyaWup().also {
        it.tarsServantRequest.servantName = "huyaliveui"
        it.tarsServantRequest.functionName = "getLivingInfo"
        it.uniAttribute.put(
          "tReq", HuyaGetLivingInfoReq(
            userInfo,
            lPresenterUid = presenterUid
          )
        )
      }.encode()
    )
    session.send(command.toByteArray())

    // create a HuyaLiveLaunchReq wup
    val liveLaunchReq = HuyaWup().also {
      it.tarsServantRequest.servantName = "liveui"
      it.tarsServantRequest.functionName = "doLaunch"
      it.uniAttribute.put("tReq", HuyaLiveLaunchReq(
        tId = userInfo,
        bSupportDomain = true
      ).also {
        it.tLiveUB.eSource = HuyaSourceType.PC_WEB.value
      })
    }
    session.send(
      HuyaSocketCommand(
        iCmdType = HuyaOperations.EWSCmd_WupReq.code,
        vData = liveLaunchReq.encode()
      ).toByteArray()
    )
  }

  override suspend fun decodeDanmu(session: WebSocketSession, data: ByteArray): List<DanmuDataWrapper?> {
    val huyaSocketCommand = HuyaSocketCommand().apply {
      readFrom(TarsInputStream(data))
    }
    when (val commandType = huyaSocketCommand.iCmdType) {
      HuyaOperations.EWSCmd_WupRsp.code -> {
        val wup = try {
          HuyaWup().apply {
            readFrom(TarsInputStream(huyaSocketCommand.vData).also {
              it.setServerEncoding(StandardCharsets.UTF_8.name())
            })
          }
        } catch (e: Exception) {
          // ignore invalid wup
          return emptyList()
        }

        logger.trace("Received wup response: ${wup.tarsServantRequest.functionName}")

        if (wup.tarsServantRequest.functionName == "doLaunch") {
          val huyaRegisterGroupReq = HuyaSocketCommand(
            iCmdType = HuyaOperations.EWSCmdC2S_RegisterGroupReq.code,
            vData = HuyaWSRegisterGroupReq().also {
              it.vGroupId = listOf("live:$presenterUid", "chat:$presenterUid")
            }.toByteArray()
          )
          session.send(huyaRegisterGroupReq.toByteArray())
          logger.trace("Sent register group request for presenterUid=$presenterUid")
        }
      }

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
              msgNotice.senderInfo.lUid,
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