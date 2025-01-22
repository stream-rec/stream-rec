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

package github.hua0512.flv.data.tag

import github.hua0512.flv.data.amf.AmfValue.Amf0Value
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FlvScriptTagDataTest : BehaviorSpec({
  given("a FlvScriptTagData with metadata values") {
    val scriptData = FlvScriptTagData(
      listOf(
        Amf0Value.String("onMetaData"),
        Amf0Value.EcmaArray(
          mapOf(
            "duration" to Amf0Value.Number(123.45),
            "width" to Amf0Value.Number(1920.0),
            "height" to Amf0Value.Number(1080.0)
          )
        )
      )
    )

    `when`("validating size calculations") {
      then("should not throw when validating size") {
        scriptData.validateSize()
      }

      then("calculated size should match actual binary size") {
        scriptData.size shouldBe scriptData.toByteArray().size
      }

      then("body size should match total size") {
        scriptData.bodySize shouldBe scriptData.size
      }

      then("individual value sizes should sum up correctly") {
        val expectedSize = scriptData.values.sumOf { it.size }
        scriptData.size shouldBe expectedSize
      }
    }

    `when`("accessing values") {
      then("should return correct value count") {
        scriptData.valuesCount shouldBe 2
      }

      then("should return correct binary data") {
        scriptData.binaryData shouldBe scriptData.toByteArray()
      }
    }
  }
}) 