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

package github.hua0512.utils.process

import java.io.File
import kotlinx.coroutines.flow.Flow

sealed class Redirect {
  /** Ignores the related stream. */
  data object SILENT : Redirect()

  /**
   * Redirect the stream to this process equivalent one.
   * In other words, it will print to the terminal if this process is also doing so.
   *
   * This is correctly using [System.out] or [System.err] depending on the stream +
   * preserving the correct order.
   * @see ProcessBuilder.Redirect.INHERIT
   * @see Consume when you want to have full control on the outcome.
   */
  data object PRINT : Redirect()

  /**
   * This will ensure that the stream content is returned as [process]'s return.
   * If both stdout and stderr are using this mode, their output will be correctly merged.
   *
   * It's also possible to consume this content without delay by using [process]'s consumer argument.
   * @see [ProcessBuilder.redirectErrorStream]
   */
  data object CAPTURE : Redirect()

  /** Override or append to a file. */
  class ToFile(val file: File, val append: Boolean = false) : Redirect()

  /**
   * Alternative to [CAPTURE] allowing to consume without delay a stream
   * without storing it in memory, and so not returned at the end of [process] invocation.
   * @see [process]'s consumer argument to consume [CAPTURE] content without delay.
   */
  class Consume(val consumer: suspend (Flow<String>) -> Unit) : Redirect()
}

internal fun Redirect.toNative() = when (this) {
  // Support jdk8: https://stackoverflow.com/a/55629297
  Redirect.SILENT -> ProcessBuilder.Redirect.to(
    File(if (System.getProperty("os.name").startsWith("Windows")) "NUL" else "/dev/null")
  )

  Redirect.PRINT -> ProcessBuilder.Redirect.INHERIT
  Redirect.CAPTURE -> ProcessBuilder.Redirect.PIPE
  is Redirect.ToFile -> when (append) {
    true -> ProcessBuilder.Redirect.appendTo(file)
    false -> ProcessBuilder.Redirect.to(file)
  }

  is Redirect.Consume -> ProcessBuilder.Redirect.PIPE
}