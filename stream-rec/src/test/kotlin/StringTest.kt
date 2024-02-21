import github.hua0512.utils.replacePlaceholders
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

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

class StringTest {


  @Test
  fun testReplace() {
    val streamer = "雪乃荔荔枝"
    val title = "新人第一天开播"
    val time = 1708461712L
    val instant = Instant.fromEpochSeconds(time)

    val fileFormat = "{streamer} - {title} - %yyyy-%MM-%dd %HH-%mm-%ss"

    val formatted = fileFormat.replacePlaceholders(streamer, title, instant)

    assertEquals("雪乃荔荔枝 - 新人第一天开播 - 2024-2-20 21-41-52", formatted)
  }
}