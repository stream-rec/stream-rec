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

package douyin

import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.douyin.danmu.DouyinDanmu
import github.hua0512.plugins.douyin.download.DouyinExtractor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.Test

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

class DouyinTest {


  private val app = App(Json).apply {
    updateConfig(AppConfig())
  }

  private val testUrl = "https://live.douyin.com/557481980778"

  @Test
  fun testUrl() = runTest {
    val url = testUrl
    val matchResult = DouyinExtractor.URL_REGEX.toRegex().find(url) ?: throw IllegalArgumentException("Invalid url")
    assertEquals("failed to match id", matchResult.groupValues.last(), "217536353956")
  }

  @Test
  fun testLive() = runTest {
    val extractor = DouyinExtractor(app.client, app.json, testUrl)
    val info = extractor.extract()
    println(info)
    assertNotNull("failed to extract", info)
  }

  @Test
  fun testDanmu() = runTest {
    val danmu = DouyinDanmu(app).apply {
      enableWrite = false
      filePath = "douyin_danmu.txt"
    }
    val init = danmu.init(Streamer("test", testUrl))
    if (init) {
      danmu.fetchDanmu()
    }
    assertNotNull("failed to get danmu", danmu)
    assert(init)
  }
}