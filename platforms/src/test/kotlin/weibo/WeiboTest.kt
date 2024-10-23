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

package weibo

import BaseTest
import github.hua0512.plugins.weibo.download.WeiboExtractor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.expect

/**
 * @author hua0512
 * @date : 2024/10/19 21:51
 */
class WeiboTest : BaseTest() {

  override val testUrl: String = "https://weibo.com/u/6034381748?is_hot=1#1573944545407"

  private fun getExtractor(url: String = testUrl): WeiboExtractor {
    return WeiboExtractor(app.client, app.json, url)
  }

  @Test
  override fun testLive() {
    val extractor = getExtractor()
    val stream = runTest {
      extractor.prepare()
      val mediaInfo = extractor.extract()
      assertNotNull(mediaInfo)
      println(mediaInfo)
    }
  }

  @Test
  fun testLive2() {
    val extractor = getExtractor("https://weibo.com/l/wblive/p/show/1022:2321325091443718094881")
    val stream = runTest {
      extractor.prepare()
      val mediaInfo = extractor.extract()
      assertNotNull(mediaInfo)
      println(mediaInfo)
    }
  }

  @Test
  override fun testRegex() {
    val extractor = getExtractor()
    expect(true) {
      extractor.match()
    }
  }

  @Test
  fun testRegex2() {
    val testUrl = "https://weibo.com/l/wblive/p/show/1022:2321325026370190442592"
    val extractor = getExtractor(testUrl)
    expect(true) {
      extractor.match()
    }
  }
}