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

package github.hua0512.plugins.huya.danmu.msg.req

import com.qq.tars.protocol.tars.TarsInputStream
import com.qq.tars.protocol.tars.TarsOutputStream
import com.qq.tars.protocol.tars.TarsStructBase
import com.qq.tars.protocol.tars.support.TarsMethodInfo
import com.qq.tars.protocol.util.TarsHelper
import com.qq.tars.rpc.protocol.tars.TarsServantRequest
import com.qq.tars.rpc.protocol.tup.UniAttribute
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Huya wup data structure
 * Extracted from https://hd2.huya.com/fedbasic/huyabaselibs/taf-signal/taf-signal.global.0.0.12.prod.js
 * @author hua0512
 * @date : 2024/8/17 21:29
 */
internal class HuyaWup() : TarsStructBase() {

  internal val tarsServantRequest = TarsServantRequest(null).also {
    it.methodInfo = TarsMethodInfo()
  }
  internal val uniAttribute = UniAttribute();


  override fun writeTo(out: TarsOutputStream) {
    with(out) {
      write(TarsHelper.VERSION3, 1)
      write(tarsServantRequest.packetType, 2)
      write(tarsServantRequest.messageType, 3)
      write(tarsServantRequest.requestId, 4)
      write(tarsServantRequest.servantName, 5)
      write(tarsServantRequest.functionName, 6)
      write(uniAttribute.encode(), 7)
      write(tarsServantRequest.timeout, 8)
      write(tarsServantRequest.context, 9)
      write(tarsServantRequest.status, 10)
    }
  }

  override fun readFrom(ins: TarsInputStream) {
    with(ins) {
      tarsServantRequest.version = read(tarsServantRequest.version, 1, false)
      tarsServantRequest.packetType = read(tarsServantRequest.packetType, 2, false)
      tarsServantRequest.messageType = read(tarsServantRequest.messageType, 3, false)
      tarsServantRequest.requestId = read(tarsServantRequest.requestId, 4, false)
      tarsServantRequest.servantName = read(tarsServantRequest.servantName, 5, false)
      tarsServantRequest.functionName = read(tarsServantRequest.functionName, 6, false)
      uniAttribute.decode(read(byteArrayOf(), 7, false))
      tarsServantRequest.timeout = read(tarsServantRequest.timeout, 8, false)
      tarsServantRequest.context = readMap(tarsServantRequest.context, 9, false)
      tarsServantRequest.status = readMap(tarsServantRequest.status, 10, false)
    }
  }


  fun encode(): ByteArray {
    val outStream = TarsOutputStream().apply {
      setServerEncoding(StandardCharsets.UTF_8.name())
      this@HuyaWup.writeTo(this)
    }

    val serializedData = outStream.toByteArray()
    val length = 4 + serializedData.size

    return ByteBuffer.allocate(length).apply {
      putInt(length)
      put(serializedData)
    }.array()
  }

  fun decode(data: ByteArray) {
    val buffer = ByteBuffer.wrap(data)
    val length = try {
      buffer.int
    } catch (e: Exception) {
      0
    }
    if (length < 4) return

    val bytes = ByteArray(length - 4)
    buffer.get(bytes)
    this.readFrom(TarsInputStream(bytes).also {
      it.setServerEncoding(StandardCharsets.UTF_8.name())
    })
  }

}