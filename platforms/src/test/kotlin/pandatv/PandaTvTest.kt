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

package pandatv

import BaseTest
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.pandatv.danmu.PandaTvDanmu
import github.hua0512.plugins.pandatv.download.PandaTvExtractor
import io.exoquery.pprint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration

/**
 * @author hua0512
 * @date : 2024/5/8 21:56
 */
class PandaTvTest : BaseTest() {

  override val testUrl: String = "https://www.pandalive.co.kr/live/play/say0716"

  @Test
  override fun testLive() = runTest {
    val extractor = PandaTvExtractor(app.client, app.json, testUrl).apply {
      prepare()
    }
    val mediaInfo = extractor.extract()
    println(pprint(mediaInfo))
  }

  @Test
  override fun testRegex() {
    val regex = PandaTvExtractor.URL_REGEX.toRegex()
    assert(regex.matches(testUrl))
    assert(regex.matchEntire(testUrl)?.groups?.get(1)?.value == "say0716")
  }

  @Test
  fun testDanmu() = runTest(timeout = Duration.INFINITE) {
    val danmu = PandaTvDanmu(app).apply {
      enableWrite = false
      filePath = "pandatv_danmu.txt"
      id = "say0716"
      userIdx = "24740200"
      token =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ2X2NmYWYwNTk5M2QiLCJleHAiOjE3MTUzNDA3NTgsImluZm8iOnsiY2hhbm5lbCI6IjI0NzQwMjAwIiwidHlwZSI6InZpZXdlciIsImR0Ijoid2ViIiwicGYiOiJ3ZWIiLCJpYXQiOjE3MTUzMzg5NTgsImlwIjoieFZCVUltOTlXT2oxVkVsRWt0azlcL1E9PSJ9LCJldGMiOnsibWMiOiIyNDc0MDIwMF8yMDI0MDUxMDA2YzkzNmU3YjcyMGU5OTIifX0.kjBDtAzo2kZKsFhGcMFfqzPiXV4-qw3Us-6AKOyP0zA"
    }
    val init = danmu.init(Streamer(0, "say0716", testUrl))
    if (init) {
      danmu.fetchDanmu()
    }
    assert(init)
  }
}