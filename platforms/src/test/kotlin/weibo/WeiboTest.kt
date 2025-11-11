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

package weibo

import BaseTest
import com.github.michaelbull.result.get
import github.hua0512.plugins.weibo.download.WeiboExtractor
import io.exoquery.kmp.pprint
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Weibo platform test
 * @author hua0512
 * @date : 2024/10/19 21:51
 */
class WeiboTest : BaseTest<WeiboExtractor>({

  test("isLive") {
    val result = extractor.extract()
    result.isOk shouldBeEqual true
    val mediaInfo = result.get()
    mediaInfo.shouldNotBeNull()
    println(pprint(mediaInfo))
  }

  test("isLiveWithRid") {
    extractor = createExtractor("https://weibo.com/l/wblive/p/show/1022:2321325091443718094881")
    extractor.prepare()
    val result = extractor.extract()
    result.isOk shouldBeEqual true
    val mediaInfo = result.get()
    mediaInfo.shouldNotBeNull()
    println(pprint(mediaInfo))
  }

  test("regex") {
    extractor.match().isOk shouldBeEqual true
  }

  test("ridRegex") {
    val testUrl = "https://weibo.com/l/wblive/p/show/1022:2321325026370190442592"
    extractor = createExtractor(testUrl)
    extractor.prepare()
    extractor.match().isOk shouldBeEqual true
  }

}) {

  override val testUrl: String = "https://weibo.com/u/6124785491"

  override fun createExtractor(url: String) = WeiboExtractor(app.client, app.json, url)
}