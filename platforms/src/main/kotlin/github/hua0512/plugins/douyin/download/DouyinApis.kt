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

package github.hua0512.plugins.douyin.download

/**
 * Douyin live room url
 * @author hua0512
 * @date : 2024/10/6 16:41
 */


internal typealias DouyinApi = DouyinApis.Companion


class DouyinApis {

  companion object {

    internal const val LIVE_DOUYIN_URL = "https://live.douyin.com"


    internal const val WEBCAST_ENTER = "${LIVE_DOUYIN_URL}/webcast/room/web/enter/"
    internal const val APP_ROOM_REFLOW = "https://webcast.amemv.com/webcast/room/reflow/info/"

    internal val webSocketDomains = arrayOf(
      "wss://webcast5-ws-web-lq.douyin.com",
      "wss://webcast5-ws-web-hl.douyin.com",
      "wss://webcast5-ws-web-lf.douyin.com"
    )
    internal val randomWebSocketUrl get() = "${webSocketDomains.random()}/webcast/im/push/v2/"
  }
}