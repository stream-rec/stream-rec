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

package github.hua0512.flv

import github.hua0512.flv.data.other.FlvMetadataInfo

/**
 * Flv meta info provider
 * @author hua0512
 * @date : 2024/9/9 1:59
 */
open class FlvMetaInfoProvider {

  private val metaInfo = mutableMapOf<Int, FlvMetadataInfo>()

  val size get() = metaInfo.size

  operator fun set(streamIndex: Int, info: FlvMetadataInfo) {
    metaInfo[streamIndex] = info
  }

  operator fun get(streamIndex: Int): FlvMetadataInfo? {
    return metaInfo[streamIndex]
  }

  fun remove(streamIndex: Int) {
    metaInfo.remove(streamIndex)
  }

  fun clear() {
    metaInfo.clear()
  }
}