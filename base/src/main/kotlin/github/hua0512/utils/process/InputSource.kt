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
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

sealed class InputSource(val redirect: ProcessBuilder.Redirect) {
  /**
   * Natively supported file redirection.
   * @see ProcessBuilder.Redirect.from
   */
  class FromFile(val file: File) : InputSource(ProcessBuilder.Redirect.from(file))

  /**
   * Allows custom logic given the [Process.getInputStream] instance,
   * auto-closed after [handler] completes.
   */
  class FromStream(val handler: suspend (OutputStream) -> Unit) : InputSource(ProcessBuilder.Redirect.PIPE)

  /**
   * Redirects input from the parent process.
   */
  class FromParent : InputSource(ProcessBuilder.Redirect.INHERIT)

  @Suppress("BlockingMethodInNonBlockingContext")
  companion object {
    @JvmStatic
    fun fromString(
      string: String,
      charset: Charset = Charset.forName("UTF-8"),
    ): InputSource = FromStream {
      it.write(string.toByteArray(charset))
    }

    @JvmStatic
    fun fromInputStream(
      inputStream: InputStream,
      bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ): InputSource = FromStream { out ->
      inputStream.use { it.copyTo(out, bufferSize) }
    }
  }
}

