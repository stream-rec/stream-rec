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

import github.hua0512.flv.exceptions.FlvTagHeaderErrorException
import kotlinx.serialization.Serializable

/**
 * Flv tag header type
 * @author hua0512
 * @date : 2024/6/8 13:37
 */
@Serializable
sealed class FlvTagHeaderType(val value: Byte) {
  @Serializable
  data object Audio : FlvTagHeaderType(8)

  @Serializable
  data object Video : FlvTagHeaderType(9)

  @Serializable
  data object ScriptData : FlvTagHeaderType(18)


  companion object {
    fun from(value: Byte): FlvTagHeaderType {
      return when (value) {
        Audio.value -> Audio
        Video.value -> Video
        ScriptData.value -> ScriptData
        else -> throw FlvTagHeaderErrorException("Invalid FLV header tag type: $value")
      }
    }
  }
}