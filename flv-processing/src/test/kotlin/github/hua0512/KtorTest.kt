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

package github.hua0512

import github.hua0512.flv.FlvMetaInfoProcessor
import github.hua0512.flv.FlvMetaInfoProvider
import github.hua0512.flv.operators.analyze
import github.hua0512.flv.operators.dump
import github.hua0512.flv.operators.process
import github.hua0512.flv.utils.asStreamFlow
import github.hua0512.plugins.StreamerContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.time.Duration

/**
 * @author hua0512
 * @date : 2024/9/27 22:00
 */

class KtorTest {


  private val streamerContext = StreamerContext("test", "")

  @Test
  fun testDownloadFlvFix(): Unit = runTest(timeout = Duration.INFINITE) {
    val client = HttpClient(OkHttp) {
      engine {
        config {
          followRedirects(true)
        }
      }
    }


    val downloadFlow = flow {
      client.prepareGet("http://pull-hs-f5.flive.douyincdn.com/thirdgame/stream-692287901379330826_or4.flv?expire=1726563916&sign=527960a8e66208a26902e8f0c4f92d0d&volcSecret=527960a8e66208a26902e8f0c4f92d0d&volcTime=1726563916&major_anchor_level=common") {
        timeout {
          requestTimeoutMillis = Long.MAX_VALUE
        }
      }
        .execute { httpResponse ->
          val channel: ByteReadChannel = httpResponse.bodyAsChannel()
          channel.asStreamFlow(context = streamerContext).collect { emit(it) }
        }
    }

    val metaInfoProvider = FlvMetaInfoProvider()
    val pathProvider = { index: Int -> "F:/test/testSample_${index}_${Clock.System.now().toEpochMilliseconds()}.flv" }
    val limitsProvider = { 0L to 3600.0f }
    client.use {
      downloadFlow
        .process(limitsProvider, streamerContext)
        .analyze(metaInfoProvider, streamerContext)
        .dump(pathProvider) { index, path, createdAt, updatedAt ->
          println("onStreamDumped: $path, $createdAt -> $updatedAt")
          launch {
            val status = FlvMetaInfoProcessor.process(path, metaInfoProvider[index]!!, true)
            if (status)
              metaInfoProvider.remove(index)
          }
        }
        .onCompletion {
          println("onCompletion...")
        }
        .collect()
    }
  }
}