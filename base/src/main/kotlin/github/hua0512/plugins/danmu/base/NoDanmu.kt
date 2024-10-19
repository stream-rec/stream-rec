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

package github.hua0512.plugins.danmu.base

import github.hua0512.app.App
import github.hua0512.data.media.DanmuDataWrapper
import github.hua0512.data.stream.Streamer
import io.ktor.websocket.WebSocketSession
import kotlinx.datetime.Instant

/**
 * No danmu implementation
 * @author hua0512
 * @date : 2024/10/20 0:08
 */
class NoDanmu(app: App) : Danmu(app) {

  override val websocketUrl: String = ""

  override val heartBeatDelay: Long = 0

  override val heartBeatPack: ByteArray = byteArrayOf()

  override suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean {
    return false
  }

  override fun oneHello(): ByteArray {
    return byteArrayOf()
  }

  override fun onDanmuRetry(retryCount: Int) {

  }

  override suspend fun decodeDanmu(
    session: WebSocketSession,
    data: ByteArray,
  ): List<DanmuDataWrapper?> {
    return emptyList()
  }
}