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

import github.hua0512.logger
import github.hua0512.utils.process.InputSource
import github.hua0512.utils.process.Redirect
import github.hua0512.utils.process.toNative
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset


suspend fun executeProcess(
  vararg command: String,
  stdin: InputSource? = null,
  stdout: Redirect = Redirect.PRINT,
  stderr: Redirect = Redirect.PRINT,
  charset: Charset = Charsets.UTF_8,
  /** Extend with new environment variables during this process's invocation. */
  env: Map<String, String>? = null,
  /** Override the process working directory. */
  directory: File? = null,
  /** Determine if process should be destroyed forcibly on job cancellation. */
  destroyForcibly: Boolean = false,
  /**
   * Callback to get the output stream of the process. This is useful for interactive processes
   */
  getOutputStream: (OutputStream) -> Unit = {},
  /**
   * Callback to get the process object. This is useful for interacting with the process object
   */
  getProcess: (Process) -> Unit = {},
  /**
   * Callback to handle the cancellation of the process. This is useful for cleaning up resources
   */
  onCancellation: (e: CancellationException) -> Unit = {},
  /** Consume without delay all streams configured with [Redirect.CAPTURE]. */
  consumer: suspend (String) -> Unit = {},
): Int {
  return withIOContext {
    coroutineScope {
      val captureAll = stdout == stderr && stderr == Redirect.CAPTURE
      val process = ProcessBuilder(*command).apply {
        stdin?.redirect?.let { redirectInput(it) }

        if (captureAll) {
          redirectErrorStream(true)
        } else {
          redirectOutput(stdout.toNative())
          redirectError(stderr.toNative())
        }

        directory?.let { directory(it) }
        env?.let { environment().putAll(it) }
      }.start()

      getProcess(process)

      // Handles async consumptions before the blocking output handling.
      if (stdout is Redirect.Consume) {
        process.inputStream.lineFlow(charset, stdout.consumer)
      }
      if (stderr is Redirect.Consume) {
        process.errorStream.lineFlow(charset, stderr.consumer)
      }


      val output = async {
        when {
          captureAll || stdout == Redirect.CAPTURE ->
            process.inputStream

          stderr == Redirect.CAPTURE ->
            process.errorStream

          else -> null
        }?.lineFlow(charset) { f ->
          f.map {
            yield()
            it.also { consumer(it) }
          }.toList()
        } ?: emptyList()
      }

      val input = async {
        (stdin as? InputSource.FromStream)?.handler?.let { handler ->
          try {
            process.outputStream.use { handler(it) }
          } catch (e: Exception) {
            logger.error("Error while writing to process input stream", e)
          } finally {
            process.outputStream.close()
          }
        }
      }
      getOutputStream(process.outputStream)
      try {
        awaitAll(output, input)
        runInterruptible { process.waitFor() }
      } catch (e: CancellationException) {
        logger.warn("Process execution was cancelled", e)
        onCancellation(e)
        when (destroyForcibly) {
          true -> process.destroyForcibly()
          false -> process.destroy()
        }
        throw e
      }
    }
  }
}

private suspend fun <T> InputStream.lineFlow(charset: Charset, block: suspend (Flow<String>) -> T): T =
  bufferedReader(charset).use { it.lineSequence().asFlow().let { f -> block(f) } }

