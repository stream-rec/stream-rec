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

package github.hua0512.data.stream

/**
 * Data class representing file information.
 *
 * @property path The path of the file.
 * @property size The size of the file in bytes.
 * @property createdAt The timestamp when the file was created, in epoch seconds.
 * @property updatedAt The timestamp when the file was last updated, in epoch seconds.
 *
 * @constructor Creates a new instance of FileInfo.
 *
 * @param path The path of the file.
 * @param size The size of the file in bytes.
 * @param createdAt The timestamp when the file was created, in epoch seconds.
 * @param updatedAt The timestamp when the file was last updated, in epoch seconds.
 *
 * @author: hua0512
 * @date Date: 2024/5/6 12:27
 */
data class FileInfo(
  val path: String,
  val size: Long,
  val createdAt: Long,
  val updatedAt: Long,
)