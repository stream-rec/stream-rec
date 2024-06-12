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

package github.hua0512.data.media

import kotlinx.datetime.Instant

/**
 * This is a sealed class that represents a wrapper for Danmu data.
 * A sealed class is used here to represent a restricted class hierarchy.
 *
 * @author hua0512
 * @date : 2024/2/11 1:21
 */
sealed class DanmuDataWrapper {

  /**
   * This data class represents the actual Danmu data.
   * It contains information about the sender, color, content, font size, server time, and client time.
   *
   * @property sender The name of the sender of the Danmu.
   * @property color The color of the Danmu.
   * @property content The content of the Danmu.
   * @property fontSize The font size of the Danmu.
   * @property serverTime The time when the server received the Danmu.
   * @property clientTime The time when the client sent the Danmu. This is optional.
   */
  data class DanmuData(
    val sender: String,
    val color: Int,
    val content: String,
    val fontSize: Int,
    val serverTime: Long,
    ) : DanmuDataWrapper()

}


data class ClientDanmuData(
  val danmu: DanmuDataWrapper,
  val videoStartTime: Instant,
  val clientTime: Double,
)