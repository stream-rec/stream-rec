import github.hua0512.app.App
import github.hua0512.download.DownloadPathProvider
import github.hua0512.flv.FlvMetaInfoProcessor
import github.hua0512.flv.FlvMetaInfoProvider
import github.hua0512.flv.operators.analyze
import github.hua0512.flv.operators.dump
import github.hua0512.flv.operators.process
import github.hua0512.flv.utils.asStreamFlow
import github.hua0512.hls.operators.processHls
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Duration

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
 * @date : 2024/6/8 12:45
 */
class NativeDownloadTest {


  @Test
  fun testDownloadLargeChunked(): Unit = runTest(timeout = Duration.INFINITE) {
    val client = App(Json {}).client


    val downloadFlow = flow {
      client.prepareGet("http://pull-hs-f5.flive.douyincdn.com/thirdgame/stream-692287901379330826_or4.flv?expire=1726563916&sign=527960a8e66208a26902e8f0c4f92d0d&volcSecret=527960a8e66208a26902e8f0c4f92d0d&volcTime=1726563916&major_anchor_level=common") {
        timeout {
          requestTimeoutMillis = Long.MAX_VALUE
        }
      }
        .execute { httpResponse ->
          val channel: ByteReadChannel = httpResponse.bodyAsChannel()
          channel.asStreamFlow().collect { emit(it) }
        }
    }

    val metaInfoProvider = FlvMetaInfoProvider()
    val pathProvider = { index: Int -> "E:/test/testSample_${index}_${Clock.System.now().toEpochMilliseconds()}.flv" }
    val limitsProvider = { 0L to 3600.0f }

    downloadFlow
      .process(limitsProvider)
      .analyze(metaInfoProvider)
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

  @Test
  fun testHlsDownload(): Unit = runTest(timeout = Duration.INFINITE) {
    val client = App(Json {}).client
    val isOneFile = false
    val pathProvider: DownloadPathProvider = { index: Int -> if (isOneFile) "F:/test/hls/testSample.ts" else "F:/test/hls" }

    val limitsProvider = { 0L to 120.0f }

    val downloadUrl =
      "http://pull-hls-q11.douyincdn.com/thirdgame/stream-692368629249344318_or4.m3u8?expire=1727769348&sign=1d15677d42367d0e9c1531e93795b1fe&major_anchor_level=common"

    downloadUrl
      .processHls(client, limitsProvider, pathProvider, isOneFile)
      .collect()
  }
}