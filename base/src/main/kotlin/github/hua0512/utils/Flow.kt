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

package github.hua0512.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Split the flow into chunks of the specified size
 * @param chunkSize the size of each chunk
 * @return a flow of lists of elements
 * @author hua0512
 * @date : 2024/5/13 20:00
 */
public fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> {
  require(size >= 1) { "Expected positive chunk size, but got $size" }
  return flow {
    var result: ArrayList<T>? = null // Do not preallocate anything
    collect { value ->
      // Allocate if needed
      val acc = result ?: ArrayList<T>(size).also { result = it }
      acc.add(value)
      if (acc.size == size) {
        emit(acc)
        // Cleanup, but don't allocate -- it might've been the case this is the last element
        result = null
      }
    }
    result?.let { emit(it) }
  }
}