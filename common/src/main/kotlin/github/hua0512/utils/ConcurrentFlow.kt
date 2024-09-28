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


import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume


interface Position {
  fun isNextAfter(other: Position?): Boolean
}

interface ConcurrentFlowCollector<in T> {
  // There is a value at this position, and here it is.
  suspend fun emitValue(position: Position, value: T)

  // There is no value at this position.
  // This may happen when the value was filtered out,
  // or if the position implementation uses some internal
  // placeholder positions.
  suspend fun emitNoValue(position: Position)
}

/**
 * ConcurrentFlow is a flow that can emit values concurrently.
 * Taken from [Git](https://gist.github.com/bugaevc/dc63d3fbc735be7a86dc713c2384efc1)
 * @author hua0512
 * @date : 2024/9/19 23:30
 */

interface ConcurrentFlow<out T> {
  // Can emit concurrently; context must be the same, except for the job.
  // That is, it's invalid to change the dispatcher; but emitting from inside
  // a scope is fine, unlike with regular flow.
  //
  // The concurrent flow is expected to pick a specific concurrency level,
  // and only try to emit that many times in parallel. Meaning, if emitValue()
  // and emitNoValue() calls suspend, the concurrent flow should not spawn more
  // coroutines to emit more values; it should wait for those in-flight calls
  // to return first. This implements backpressure.
  suspend fun collectInternal(collector: ConcurrentFlowCollector<T>)
}

// Wraps a regular flow into a ConcurrentFlow.
private class ConcurrentFlowImpl<T>(
  private val flow: Flow<T>,
  private val concurrencyLevel: Int,
) : ConcurrentFlow<T> {
  // A position implementation that wraps a simple index.
  private data class IndexPosition(
    private val index: Int,
  ) : Position {
    override fun isNextAfter(other: Position?): Boolean {
      if (other == null) {
        return index == 0
      }
      if (other !is IndexPosition) {
        throw IllegalArgumentException()
      }
      return index == other.index + 1
    }
  }

  override suspend fun collectInternal(
    collector: ConcurrentFlowCollector<T>,
  ) {
    val semaphore = Semaphore(concurrencyLevel)
    coroutineScope {
      flow.collectIndexed { index, value ->
        // Block further upstream flow collection
        // until a slot frees up.
        semaphore.acquire()
        // Once we have acquired the semaphore, do
        // not block the upstream any further.
        launch {
          try {
            collector.emitValue(IndexPosition(index), value)
          } finally {
            semaphore.release()
          }
        }
      }
    }
  }
}

/**
 * Turn this flow into a [ConcurrentFlow].
 *
 * The following operations like [map], [filter], [flatMap], [reduce], [collect],
 * will be run concurrently rather than sequentially.
 */
fun <T> Flow<T>.concurrent(
  concurrencyLevel: Int = 64,
): ConcurrentFlow<T> = ConcurrentFlowImpl(this, concurrencyLevel)

/**
 * Collect this concurrent flow, concurrently.
 *
 * The [block] will be invoked concurrently to collect the items.
 * There's no guarantee about ordering of the items.
 */
suspend fun <T> ConcurrentFlow<T>.collect(block: suspend (T) -> Unit) {
  collectInternal(object : ConcurrentFlowCollector<T> {
    override suspend fun emitValue(position: Position, value: T) {
      block(value)
    }

    override suspend fun emitNoValue(position: Position) {
      // Do nothing.
    }
  })
}

private class ConcurrentMap<T, R>(
  private val upstream: ConcurrentFlow<T>,
  private val transform: suspend (T) -> R,
) : ConcurrentFlow<R> {

  override suspend fun collectInternal(
    collector: ConcurrentFlowCollector<R>,
  ) {
    upstream.collectInternal(object : ConcurrentFlowCollector<T> {
      override suspend fun emitValue(position: Position, value: T) {
        val transformed: R = transform(value)
        collector.emitValue(position, transformed)
      }

      override suspend fun emitNoValue(position: Position) {
        collector.emitNoValue(position)
      }
    })
  }
}

/**
 * Map a function over this concurrent flow, concurrently.
 *
 * The [transform] block will be invoked to transform each item, concurrently.
 */
fun <T, R> ConcurrentFlow<T>.map(
  transform: suspend (T) -> R,
): ConcurrentFlow<R> = ConcurrentMap(this, transform)

private class ConcurrentFilter<T>(
  private val upstream: ConcurrentFlow<T>,
  private val predicate: suspend (T) -> Boolean,
) : ConcurrentFlow<T> {

  override suspend fun collectInternal(
    collector: ConcurrentFlowCollector<T>,
  ) {
    upstream.collectInternal(object : ConcurrentFlowCollector<T> {
      override suspend fun emitValue(position: Position, value: T) {
        val matches = predicate(value)
        when {
          matches -> collector.emitValue(position, value)
          else -> collector.emitNoValue(position)
        }
      }

      override suspend fun emitNoValue(position: Position) {
        collector.emitNoValue(position)
      }
    })
  }
}

/**
 * Filter this concurrent flow, concurrently.
 *
 * The [predicate] block will be invoked to keep or drop each item,
 * concurrently.
 */
fun <T> ConcurrentFlow<T>.filter(
  predicate: suspend (T) -> Boolean,
): ConcurrentFlow<T> = ConcurrentFilter(this, predicate)

fun <T> ConcurrentFlow<T?>.filterNotNull(): ConcurrentFlow<T> {
  @Suppress("UNCHECKED_CAST")
  return filter { value -> value != null } as ConcurrentFlow<T>
}

inline fun <reified T> ConcurrentFlow<*>.filterIsInstance(): ConcurrentFlow<T> {
  @Suppress("UNCHECKED_CAST")
  return filter { value -> value is T } as ConcurrentFlow<T>
}

/**
 * Reduce this concurrent flow, concurrently.
 *
 * The [operation] block should be associative and commutative,
 * there's no guarantee about the order in which it is applied
 * to the items.
 */
suspend fun <T, S : T> ConcurrentFlow<T>.reduce(
  operation: suspend (accumulator: S, value: T) -> S,
): S {
  val empty = Any()
  val accumulator: AtomicReference<Any?> = AtomicReference(empty)

  collectInternal(object : ConcurrentFlowCollector<T> {
    override suspend fun emitValue(position: Position, value: T) {
      var v = value
      while (true) {
        val acc = accumulator.get()
        if (acc === empty) {
          val exchanged = accumulator
            .weakCompareAndSetPlain(empty, v)
          when {
            exchanged -> return
            else -> continue
          }
        } else {
          val exchanged = accumulator
            .weakCompareAndSetPlain(acc, empty)
          if (!exchanged) {
            continue
          }
        }
        // At this point, acc is not empty.
        @Suppress("UNCHECKED_CAST")
        v = operation(acc as S, v)
      }
    }

    override suspend fun emitNoValue(position: Position) {
      // Do nothing.
    }
  })

  val acc = accumulator.get()
  if (acc == empty) {
    throw NoSuchElementException("Empty flow can't be reduced")
  }
  @Suppress("UNCHECKED_CAST")
  return acc as S
}

private class ConcurrentFlatten<T>(
  private val upstream: ConcurrentFlow<Flow<T>>,
) : ConcurrentFlow<T> {
  // A position inside a nested flow, or after one.
  private data class NestedPosition(
    private val outerPosition: Position,
    private val innerIndex: Int,
    private val isSentinel: Boolean,
  ) : Position {
    override fun isNextAfter(other: Position?): Boolean {
      if (other == null) {
        return outerPosition.isNextAfter(null) && innerIndex == 0
      }
      if (other !is NestedPosition) {
        throw IllegalArgumentException()
      }
      return other.isSentinel && innerIndex == 0 &&
              outerPosition.isNextAfter(other.outerPosition)
    }
  }

  override suspend fun collectInternal(
    collector: ConcurrentFlowCollector<T>,
  ) {
    upstream.collectInternal(object : ConcurrentFlowCollector<Flow<T>> {
      override suspend fun emitValue(
        position: Position,
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        flow: Flow<T>,
      ) {
        var lastIndex = -1
        flow.collectIndexed { index, value ->
          lastIndex = index
          val np = NestedPosition(
            outerPosition = position,
            innerIndex = index,
            isSentinel = false,
          )
          collector.emitValue(np, value)
        }
        val np = NestedPosition(
          outerPosition = position,
          innerIndex = lastIndex + 1,
          isSentinel = true,
        )
        collector.emitNoValue(np)
      }

      override suspend fun emitNoValue(position: Position) {
        val np = NestedPosition(
          outerPosition = position,
          innerIndex = 0,
          isSentinel = true,
        )
        collector.emitNoValue(np)
      }
    })
  }
}

/**
 * Flatten a concurrent flow of flows into a concurrent flow.
 */
fun <T> ConcurrentFlow<Flow<T>>.flatten(): ConcurrentFlow<T> =
  ConcurrentFlatten(this)

fun <T, R> ConcurrentFlow<T>.flatMap(
  transform: suspend (T) -> Flow<R>,
): ConcurrentFlow<R> = map(transform).flatten()

private class MergeKeepingOrder<T>(
  private val flow: ConcurrentFlow<T>,
  private val channel: SendChannel<T>,
) {
  private val empty = Any()
  private var lastEmittedPosition: Position? = null

  private data class PendingEmission(
    val position: Position,
    val value: Any?,
  )

  private val pendingEmissions = mutableListOf<PendingEmission>()
  private val continuations = LinkedList<Continuation<Unit>>()
  private val moveForwardMutex = Mutex()

  private suspend fun moveForward() {
    moveForwardMutex.tryLock() || return
    try {
      while (true) {
        val pe: PendingEmission
        val continuation: Continuation<Unit>?
        synchronized(this) {
          val iter: MutableIterator<PendingEmission> =
            pendingEmissions.iterator()
          for (pendingEmission in iter) {
            val isNext = pendingEmission.position
              .isNextAfter(lastEmittedPosition)
            if (!isNext) {
              continue
            }
            iter.remove()
            pe = pendingEmission
            lastEmittedPosition = pe.position
            continuation = when {
              pendingEmissions.size < 64 &&
                      continuations.isNotEmpty() ->
                continuations.remove()

              else -> null
            }
            // Proceed to send the value (if any)
            // with the monitor released.
            return@synchronized
          }
          // Have not found the next pending emission.
          // Make sure to unlock the mutex before the monitor.
          moveForwardMutex.unlock()
          return@moveForward
        }
        if (pe.value !== empty) {
          @Suppress("UNCHECKED_CAST")
          channel.send(pe.value as T)
        }
        continuation?.resume(Unit)
      }
    } catch (ex: Throwable) {
      moveForwardMutex.unlock()
      throw ex
    }
  }

  private suspend fun blockBuffer() {
    suspendCancellableCoroutine<Unit> { cont ->
      synchronized(this) {
        if (pendingEmissions.size < 64) {
          cont.resume(Unit)
          return@suspendCancellableCoroutine
        }
        continuations.add(cont)
        cont.invokeOnCancellation {
          synchronized(this) {
            continuations.remove(cont)
          }
        }
      }
    }
  }

  suspend fun collect() {
    flow.collectInternal(object : ConcurrentFlowCollector<T> {
      override suspend fun emitValue(position: Position, value: T) {
        synchronized(this@MergeKeepingOrder) {
          val pendingEmission = PendingEmission(
            position = position,
            value = value,
          )
          pendingEmissions.add(pendingEmission)
        }
        moveForward()
        blockBuffer()
      }

      override suspend fun emitNoValue(position: Position) {
        synchronized(this@MergeKeepingOrder) {
          val pendingEmission = PendingEmission(
            position = position,
            value = empty,
          )
          pendingEmissions.add(pendingEmission)
        }
        moveForward()
        blockBuffer()
      }
    })
  }
}

/**
 * Merge a concurrent flow mack into a regular, sequential flow.
 *
 * If [preserveOrder] is true, items are emitted in the same order they were
 * present in the initial flow; otherwise, in an arbitrary order. Note that
 * preserving order requires additional buffering and means the collector has
 * to wait for prior items to become available instead of processing new items
 * as they appear, which slows things down.
 */
fun <T> ConcurrentFlow<T>.merge(preserveOrder: Boolean): Flow<T> = channelFlow {
  if (preserveOrder) {
    MergeKeepingOrder(
      flow = this@merge,
      channel = this,
    ).collect()
  } else {
    collect { value ->
      send(value)
    }
  }
}