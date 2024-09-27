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

package github.hua0512.hls

import github.hua0512.download.DownloadPathProvider
import github.hua0512.hls.operators.downloadHls
import github.hua0512.hls.operators.process
import github.hua0512.plugins.StreamerContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration

/**
 * @author hua0512
 * @date : 2024/9/27 22:03
 */
class KtorTest {


  @Test
  fun testHlsDownload(): Unit = runTest(timeout = Duration.INFINITE) {
    val client = HttpClient(OkHttp) {
      engine {
        config {
          followRedirects(true)
        }
      }
    }

    val isOneFile = false
    val pathProvider: DownloadPathProvider = { index: Int -> if (isOneFile) "F:/test/hls/testSample.ts" else "F:/test/hls" }

    val limitsProvider = { 0L to 120.0f }

    val downloadUrl =
      "http://pull-hls-q11.douyincdn.com/thirdgame/stream-692368629249344318_or4.m3u8?expire=1727769348&sign=1d15677d42367d0e9c1531e93795b1fe&major_anchor_level=common"

    val context = StreamerContext("test", "")

    client.use {
      downloadUrl
        .downloadHls(client, context)
        .process(context, limitsProvider, pathProvider, isOneFile, { _, _ -> }, { _, _, _ -> })
        .collect()
    }
  }

}