///*
// * MIT License
// *
// * Stream-rec  https://github.com/hua0512/stream-rec
// *
// * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// * SOFTWARE.
// */
//
//package github.hua0512.flv.benchmark
//
//import github.hua0512.flv.utils.CRC32Sink
//import github.hua0512.utils.crc32
//import kotlinx.io.Buffer
//import kotlinx.io.buffered
//import kotlinx.io.discardingSink
//import kotlinx.io.readByteArray
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
///**
// * @author hua0512
// * @date : 2024/12/31 20:58
// */
//
//@State(Scope.Benchmark)
//@Fork(1)
//@Warmup(iterations = 10)
//@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
//class Crc32Benchmark {
//
//
//  @Param("1024", "10240", "102400", "31457280")
//  var testSize: Int = 0
//
//
//  private lateinit var testData: ByteArray
//
//  @Setup
//  fun setup() {
//    testData = ByteArray(testSize)
//    testData.fill(0x55.toByte())
//  }
//
//  @TearDown
//  fun cleanup() {
//    testData = ByteArray(0)
//  }
//
//
//  @Benchmark
//  fun crc32ByJava(): Long {
//    val crc32 = testData.crc32()
//    return crc32
//  }
//
//
//  @Benchmark
//  fun crc32BySource(): Long {
//    val buffer = Buffer()
//    buffer.write(testData)
//
//    val crc32Source = CRC32Sink(discardingSink())
//    crc32Source.buffered().use {
//      val copyBuffer = buffer.copy()
//      it.write(copyBuffer.readByteArray())
//    }
//    buffer.close()
//    return crc32Source.crc32().toLong()
//  }
//
//}