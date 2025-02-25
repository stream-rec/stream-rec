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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

/**
 * This function is a helper function to run a block of code in IO context.
 *
 * @param block The block of code to be executed in IO context.
 * @return The result of the block execution.
 */
@OptIn(ExperimentalContracts::class)
suspend fun <T> withIOContext(context: CoroutineContext? = null, block: suspend CoroutineScope.() -> T): T {

  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }

  val context = if (context != null) Dispatchers.IO + context else Dispatchers.IO

  return withContext(context) {
    block()
  }
}

/**
 * This function is a helper function to retry a block of code in case of IOException.
 * It uses exponential backoff strategy for retrying.
 *
 * @param maxRetries The maximum number of retries. Default is 3.
 * @param initialDelayMillis The initial delay before retrying. Default is 1000ms.
 * @param maxDelayMillis The maximum delay before retrying. Default is 5000ms.
 * @param factor The factor by which the delay increases after each retry. Default is 2.0.
 * @param onError A function to be called when an IOException occurs. It takes the exception and the retry count as parameters.
 * @param block The block of code to be retried.
 * @return The result of the block execution.
 * @throws IOException If the maximum number of retries is reached and the block still fails.
 * @throws IllegalStateException If the function somehow reaches an unreachable statement.
 */
suspend inline fun <T> withIORetry(
  maxRetries: Int = 3,
  initialDelayMillis: Long = 1000,
  maxDelayMillis: Long = 5000,
  factor: Double = 2.0,
  onError: ((e: Exception, retryCount: Int) -> Unit) = { _, _ -> },
  block: suspend () -> T,
): T = withRetry<IOException, T>(maxRetries, initialDelayMillis, maxDelayMillis, factor, onError, block)


suspend inline fun <reified T : Exception, U> withRetry(
  maxRetries: Int = 3,
  initialDelayMillis: Long = 1000,
  maxDelayMillis: Long = 5000,
  factor: Double = 2.0,
  onError: ((e: Exception, retryCount: Int) -> Unit),
  block: suspend () -> U,
): U {
  var currentDelay = initialDelayMillis
  repeat(maxRetries) { retryCount ->
    try {
      return block()
    } catch (e: Exception) {
      if (e !is T || retryCount == maxRetries - 1) {
        // If we've reached the maximum retries, propagate the exception
        throw e
      }
      onError(e, retryCount)
    }
    delay(currentDelay)
    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
  }
  // This should not be reached
  throw IllegalStateException("Unreachable statement")
}