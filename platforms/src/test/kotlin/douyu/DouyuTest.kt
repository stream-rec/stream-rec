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

package douyu

import BaseTest
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.douyu.danmu.DouyuDanmu
import github.hua0512.plugins.douyu.download.DouyuExtractor
import github.hua0512.plugins.douyu.download.extractDouyunRidFromUrl
import io.exoquery.pprint
import kotlinx.coroutines.test.runTest
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

/**
 * @author hua0512
 * @date : 2024/3/22 13:42
 */
class DouyuTest : BaseTest() {

  override val testUrl = "https://www.douyu.com/topic/wwqymadrid?rid=8984762&dyshid=19bcae2-ced06e4edcaf43bb8f2bb9c500041601"

  @Test
  override fun testRegex() {
    val match = Regex("""^https:\/\/www\.douyu\.com.*""").matches(testUrl)
    assert(match)
  }

  @Test
  fun testVideoLoopRegex() {
    val match = Regex(""""videoLoop":\s*(\d+)""").find("""{"videoLoop":1,"tencentIdent":0,"clubOrgName":"壹花一海",}""")
    assert(match?.groupValues?.get(1) == "1")
  }

  @Test
  override fun testLive() = runTest {
    val extractor = DouyuExtractor(app.client, app.json, testUrl).apply {
      prepare()
    }
    val mediaInfo = extractor.extract()
    assert(mediaInfo != null)
    println(pprint(mediaInfo))
  }

  @Test
  fun testLiveStatusRegex() {
    val liveStatusRegex = "\\\$ROOM\\.show_status\\s*=\\s*(\\d+);"
    val liveStatus = liveStatusRegex.toRegex().find("""${"$"}ROOM.show_status = 1; """)?.groupValues?.get(1)
    assert(liveStatus == "1")
  }

  @Test
  fun testDanmu() = runTest {
    val danmu = DouyuDanmu(app).apply {
      rid = "8984762"
      enableWrite = false
      filePath = "douyu_danmu.txt"
    }
    val init = danmu.init(Streamer(0, "EQ118", testUrl))
    if (init) {
      danmu.fetchDanmu()
    }
    assert(init)
  }

  @Test
  fun testExtractRidFromUrl() = runTest {
    val rid = extractDouyunRidFromUrl(testUrl)
    assert(rid == "8984762")
  }
}