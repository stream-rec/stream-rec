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

package twitch

import BaseTest
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.twitch.danmu.TwitchDanmu
import github.hua0512.plugins.twitch.download.TwitchExtractor
import io.exoquery.pprint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration

/**
 * Twitch platform test
 * @author hua0512
 * @date : 2024/4/27 22:05
 */
class TwitchTest : BaseTest() {

  override val testUrl: String = "https://www.twitch.tv/aspaszin"

  @Test
  override fun testLive() = runTest {
    val extractor = TwitchExtractor(app.client, app.json, testUrl).apply {
      prepare()
    }
    val mediaInfo = extractor.extract()
    println(pprint(mediaInfo))
  }

  @Test
  override fun testRegex() {
    val regex = TwitchExtractor.URL_REGEX.toRegex()
    val matchResult = regex.find(testUrl)
    assert(matchResult != null)
    assert(matchResult!!.groupValues[1] == "aspaszin")
  }

  @Test
  fun testDanmu() = runTest(timeout = Duration.INFINITE) {
    val danmu = TwitchDanmu(app).apply {
      channel = "aspaszin"
      enableWrite = false
      filePath = "twitch_danmu.txt"
    }
    val init = danmu.init(Streamer(0, "aspaszin", testUrl))
    if (init) {
      danmu.fetchDanmu()
    }
    assert(init)
  }
}