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

package douyu

import BaseTest
import com.github.michaelbull.result.get
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.douyu.danmu.DouyuDanmu
import github.hua0512.plugins.douyu.download.DouyuExtractor
import github.hua0512.plugins.douyu.download.extractDouyunRidFromUrl
import io.exoquery.kmp.pprint
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * @author hua0512
 * @date : 2024/3/22 13:42
 */
class DouyuTest : BaseTest<DouyuExtractor>({

  test("regex") {
    val match = Regex("""^https://www\.douyu\.com.*""").matches(testUrl)
    match shouldBeEqual true
  }

  test("isLive") {
    val result = extractor.extract()

    result.isOk shouldBeEqual true
    val mediaInfo = result.get()
    mediaInfo.shouldNotBeNull()
    println(pprint(mediaInfo))
  }

  test("liveStatusRegex") {
    val liveStatusRegex = "\\\$ROOM\\.show_status\\s*=\\s*(\\d+);"
    val liveStatus = liveStatusRegex.toRegex().find("""${"$"}ROOM.show_status = 1; """)?.groupValues?.get(1)
    liveStatus.shouldNotBeNull()
    liveStatus shouldBeEqual "1"
  }

  test("videoLoopRegex") {
    val videoLoop = """{"videoLoop":1,"tencentIdent":0,"clubOrgName":"壹花一海",}"""
    val match = Regex(""""videoLoop":\s*(\d+)""").find(videoLoop)
    match.shouldNotBeNull()
    match.groupValues[1] shouldBeEqual "1"
  }

  test("ridFromUrl") {
    val rid = extractDouyunRidFromUrl(testUrl)
    rid.shouldNotBeNull()
    rid shouldBeEqual "288016"
  }

  test("danmu") {
    val result = extractor.extract()
    result.isOk shouldBeEqual true
    val mediaInfo = result.get()
    mediaInfo.shouldNotBeNull()
    val danmu = DouyuDanmu(app).apply {
      rid = extractor.rid
      enableWrite = false
      filePath = "douyu_danmu.txt"
    }
    val init = danmu.init(Streamer(0, "test", testUrl))
    if (init) {
      danmu.fetchDanmu()
    }
    init shouldBeEqual true
  }

}) {

  override val testUrl =
    "https://www.douyu.com/288016"

  override fun createExtractor(url: String) = DouyuExtractor(app.client, app.json, testUrl)
}