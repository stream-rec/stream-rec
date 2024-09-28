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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.cancellation.CancellationException

/**
 * Returns a flow with elements equal to `map(f)`, including having the same order, but evaluates up
 * to `maxConcurrency` of the flow values concurrently, up to a limit of `buffer` elements ahead of
 * the consumer.
 *
 * For example, `flowOf(a, b, c, d, e).mapConcurrently(maxConcurrency = 3, buffer = 4, f)` will
 * evaluate `f(a)`, `f(b)`, and `f(c)` concurrently, and will start evaluating `f(d)` as soon as
 * one of those complete, but `f(e)` will not start until `f(a)` is collected.
 *
 * If `x` is emitted by the backing flow and `f(x)` throws an exception, the returned flow
 * will attempt to cancel the evaluation of `f` on any values emitted after `x`, but will continue
 * evaluating `f` on values emitted before `x`.  So in the above example, if `f(b)` throws before
 * `f(a)` or `f(c)` complete, `f(c)` will be cancelled but `f(a)` will be allowed to complete.
 */
public fun <T, R> Flow<T>.mapConcurrently(
  maxConcurrency: Int,
  buffer: Int,
  f: suspend (T) -> R,
): Flow<R> {
  require(maxConcurrency > 0) { "Expected maxConcurrency to be > 0 but was $maxConcurrency" }
  require(buffer > 1) { "Expected buffer to be > 1 but was $buffer" }
  return flow {
    /*
     * This has lots of moving parts, unfortunately, so here's a sketch of what's going on.
     *
     * First, the semaphore controls concurrency on evaluating f *and* on getting upstream elements.
     * So in a flow emitting a, b, c with maxConcurrency = 3, there can be three concurrent tasks:
     * computing f(a) and f(b) and getting c from upstream.  Thus, we acquire permits *before*
     * collecting the element they're associated with, including before the first element,
     * and release them after computing f on that element.
     *
     * The relationship between collecting the upstream flow and launching the downstream results
     * is unusual, because an exception in f should cancel its "parent," the upstream flow
     * collection, but not all of its siblings, since prior elements may still be in progress.
     * Normally arranged coroutine scopes simply won't permit that, so we have to make the upstream
     * flow collection a sibling task of evaluating f on its elements, requiring a separate channel
     * and collection job.
     *
     * To achieve the effect that f(x) cancels f on values that come after x, but not values that
     * came before, we maintain an implicit linked list of CompletableDeferreds.
     * exceptionWasThrownEarlier completes exceptionally if f threw on any element
     * "before this one," and exceptionWasThrownEarlierOrHere completes exceptionally if f threw
     * on any element before this one, or on this one; we install completion handlers that propagate
     * appropriately.
     */
    val semaphore = Semaphore(permits = maxConcurrency, acquiredPermits = 1)

    supervisorScope {
      val channel = Channel<T>(0)
      val upstreamJob = launch {
        val upstreamCollectExceptionOrNull = runCatching {
          collect {
            channel.send(it)
            semaphore.acquire()
          }
        }.exceptionOrNull()
        channel.close(upstreamCollectExceptionOrNull)
      }

      var exceptionWasThrownEarlier = CompletableDeferred<Nothing>()
      while (true) {
        val tResult = try {
          select<ChannelResult<T>> {
            channel.onReceiveCatching { it }
            exceptionWasThrownEarlier.onAwait { it } // throws the exception
          }
        } catch (thrown: Throwable) {
          upstreamJob.cancel(thrown.asCancellation())
          break
        }
        if (tResult.isClosed) {
          val ex = tResult.exceptionOrNull()
          if (ex != null) {
            emit(async { throw ex })
          }
          break
        }
        val t = tResult.getOrThrow()

        // Deferred that will be completed exceptionally if evaluating f on any value before t, or
        // on t itself, threw.
        val exceptionWasThrownEarlierOrHere = CompletableDeferred<Nothing>()

        val evalF = async { f(t) }
        evalF.invokeOnCompletion { thrown ->
          if (thrown != null) {
            exceptionWasThrownEarlierOrHere.completeExceptionally(thrown)
          } else {
            semaphore.release()
          }
        }
        exceptionWasThrownEarlier.invokeOnCompletion { thrown -> // should never be null
          // don't nest CancellationExceptions arbitrarily deep
          evalF.cancel(thrown!!.asCancellation())

          // it's possible that evalF completed successfully, but there are other downstream f's to
          // cancel, so we can't depend on the evalF completion handler to propagate thrown
          exceptionWasThrownEarlierOrHere.completeExceptionally(thrown)
        }
        emit(evalF)
        exceptionWasThrownEarlier = exceptionWasThrownEarlierOrHere
      }
    }
  }
    .buffer(if (buffer == Int.MAX_VALUE) buffer else buffer - 2)
    // one async can be started but unbuffered, and one can be awaiting; the -2 is necessary to
    // ensure exactly what the doc describes
    .map { it.await() }
}

private fun Throwable.asCancellation(): CancellationException =
  this as? CancellationException ?: CancellationException(null, this)