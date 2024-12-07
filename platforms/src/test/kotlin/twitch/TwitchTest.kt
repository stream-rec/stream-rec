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

package twitch

import BaseTest
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.twitch.danmu.TwitchDanmu
import github.hua0512.plugins.twitch.download.TwitchExtractor
import io.exoquery.kmp.pprint
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Twitch platform test
 * @author hua0512
 * @date : 2024/4/27 22:05
 */
class TwitchTest : BaseTest<TwitchExtractor>({

  test("isLive") {
    val result = extractor.extract()
    result.isOk shouldBeEqual true
    val mediaInfo = result.value
    println(pprint(mediaInfo))
  }

  test("regex") {
    val regex = TwitchExtractor.URL_REGEX.toRegex()
    val matchResult = regex.find(testUrl)
    matchResult.shouldNotBeNull()
    matchResult.groupValues[1] shouldBeEqual "aspaszin"

    val extract = extractor.match()
    extract.shouldNotBeNull()
    extract.isOk shouldBeEqual true
    extract.value shouldBeEqual "aspaszin"
  }

  test("danmu") {
    val danmu = TwitchDanmu(app).apply {
      channel = "aspaszin"
      enableWrite = false
      filePath = "twitch_danmu.txt"
    }
    val init = danmu.init(Streamer(0, "aspaszin", testUrl))
    init shouldBeEqual true
    danmu.isInitialized.get() shouldBeEqual true
    if (init) {
      danmu.fetchDanmu()
    }
  }

}) {

  override val testUrl: String = "https://www.twitch.tv/aspaszin"

  override fun createExtractor(url: String) = TwitchExtractor(app.client, app.json, url)
}