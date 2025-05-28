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

package github.hua0512.utils

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Internal state for the RateLimiter, managed atomically.
 */
private data class RateLimiterState(
  val tokens: Double,
  val lastRefillNanos: Long,
  val lastAcquireCompletionNanos: Long,
)

/**
 * A token bucket rate limiter implementation, designed for high concurrency and performance
 * by using atomic operations instead of mutexes.
 * It supports a maximum burst size and a minimum delay between acquisitions.
 */
class RateLimiter(
  permitsPerSecond: Long,
  initialMinDelayMs: Long,
  maxBurst: Int = 1,
) {
  init {
    require(permitsPerSecond >= 0) { "permitsPerSecond must be non-negative" }
    require(initialMinDelayMs >= 0) { "initialMinDelayMs must be non-negative" }
    require(maxBurst >= 0) { "maxBurst must be non-negative, will be coerced to at least 1 for bucket size" }
  }

  private val bucketSize: Double = maxBurst.coerceAtLeast(1).toDouble()
  private val refillIntervalNanos: Double = if (permitsPerSecond > 0) {
    1_000_000_000.0 / permitsPerSecond
  } else {
    Double.POSITIVE_INFINITY // No refill if rate is 0
  }

  private val _minDelayMsInternal = atomic(initialMinDelayMs)

  private val state: AtomicRef<RateLimiterState>

  init {
    val initialTimeNanos = System.nanoTime()
    // Ensure the first acquire is not blocked by minDelay if minDelay is set
    val effectiveInitialMinDelayNanos = initialMinDelayMs * 1_000_000L
    state = atomic(
      RateLimiterState(
        tokens = bucketSize,
        lastRefillNanos = initialTimeNanos,
        lastAcquireCompletionNanos = initialTimeNanos - effectiveInitialMinDelayNanos
      )
    )
  }

  /**
   * Current minimum delay between permits in milliseconds.
   */
  val minDelayMs: Long
    get() = _minDelayMsInternal.value

  /**
   * Updates the minimum delay between permits.
   * @param newMinDelayMs New minimum delay in milliseconds. Must be non-negative.
   */
  fun updateMinDelay(newMinDelayMs: Long) {
    require(newMinDelayMs >= 0) { "newMinDelayMs must be non-negative" }
    if (newMinDelayMs == _minDelayMsInternal.value) {
      return // No change, no need to update
    }
    _minDelayMsInternal.value = newMinDelayMs
  }

  /**
   * Acquires a permit from the rate limiter, suspending if necessary.
   * This function is thread-safe and designed for high concurrency.
   * @return The duration for which the caller was delayed, if any.
   */
  suspend fun acquire(): Duration {
    while (true) {
      val acquireStartTimeNanos = System.nanoTime()
      val currentMinDelaySettingMs = _minDelayMsInternal.value // Read once per loop iteration

      val currentState = state.value
      val currentTokens = currentState.tokens
      val currentLastRefillNanos = currentState.lastRefillNanos
      val currentLastAcquireCompletionNanos = currentState.lastAcquireCompletionNanos

      // 1. Calculate wait time due to minDelayMs constraint
      var waitDueToMinDelayNanos = 0L
      if (currentMinDelaySettingMs > 0L) {
        val minDelayNanos = currentMinDelaySettingMs * 1_000_000L
        val earliestNextAcquireTime = currentLastAcquireCompletionNanos + minDelayNanos
        if (acquireStartTimeNanos < earliestNextAcquireTime) {
          waitDueToMinDelayNanos = earliestNextAcquireTime - acquireStartTimeNanos
        }
      }

      // Effective time for token calculation is after potential minDelay wait
      val effectiveTimeForTokens = acquireStartTimeNanos + waitDueToMinDelayNanos

      // 2. Refill tokens based on elapsed time
      val timeSinceLastRefill = effectiveTimeForTokens - currentLastRefillNanos
      var newTokensFromRefill = 0.0
      var nextLastRefillTimeNanos = currentLastRefillNanos
      if (timeSinceLastRefill > 0) { // Ensure time has progressed
        newTokensFromRefill = timeSinceLastRefill / refillIntervalNanos
        nextLastRefillTimeNanos = effectiveTimeForTokens // Refill up to this point
      }
      val tokensAfterRefill = (currentTokens + newTokensFromRefill).coerceAtMost(bucketSize)

      // 3. Calculate wait time due to token availability
      var waitDueToTokensNanos = 0L
      var finalTokensInBucket: Double
      if (tokensAfterRefill >= 1.0) {
        finalTokensInBucket = tokensAfterRefill - 1.0
      } else {
        val tokensNeeded = 1.0 - tokensAfterRefill
        waitDueToTokensNanos = (tokensNeeded * refillIntervalNanos).toLong()
        finalTokensInBucket = 0.0 // After waiting, we've consumed the conceptual token
      }

      val totalWaitNanos = max(waitDueToMinDelayNanos, waitDueToTokensNanos)

      // 4. If waiting is needed, suspend execution
      if (totalWaitNanos > 0) {
        delay(totalWaitNanos.nanoseconds)
      }

      // 5. Attempt to atomically update the state
      // The acquire completion time is the time *after* any delay
      val actualAcquireCompletionTimeNanos = acquireStartTimeNanos + totalWaitNanos
      // Note: if delay occurred, System.nanoTime() now would be later.
      // Using `acquireStartTimeNanos + totalWaitNanos` as the logical completion time for state update.

      val newProposedState = RateLimiterState(
        tokens = finalTokensInBucket,
        lastRefillNanos = nextLastRefillTimeNanos, // Reflects refill up to effectiveTimeForTokens
        lastAcquireCompletionNanos = actualAcquireCompletionTimeNanos
      )

      if (state.compareAndSet(currentState, newProposedState)) {
        return totalWaitNanos.nanoseconds // Return the calculated delay
      }
      // CAS failed, another thread updated the state. Loop again.
    }
  }

  /**
   * Tries to acquire a permit without waiting.
   * This function is thread-safe and non-blocking in its decision logic.
   * @return `true` if a permit was acquired immediately, `false` otherwise.
   */
  fun tryAcquire(): Boolean {
    while (true) {
      val evaluationTimeNanos = System.nanoTime()
      val currentMinDelaySettingMs = _minDelayMsInternal.value

      val currentState = state.value
      val currentTokens = currentState.tokens
      val currentLastRefillNanos = currentState.lastRefillNanos
      val currentLastAcquireCompletionNanos = currentState.lastAcquireCompletionNanos

      // 1. Check minDelayMs constraint
      if (currentMinDelaySettingMs > 0L) {
        val minDelayNanos = currentMinDelaySettingMs * 1_000_000L
        val earliestNextAcquireTime = currentLastAcquireCompletionNanos + minDelayNanos
        if (evaluationTimeNanos < earliestNextAcquireTime) {
          return false // Would need to wait due to minDelay
        }
      }

      // 2. Refill tokens (effective time is now, as no minDelay wait is considered for tryAcquire pass/fail)
      val effectiveTimeForTokens = evaluationTimeNanos
      val timeSinceLastRefill = effectiveTimeForTokens - currentLastRefillNanos
      var newTokensFromRefill = 0.0
      var nextLastRefillTimeNanos = currentLastRefillNanos
      if (timeSinceLastRefill > 0) {
        newTokensFromRefill = timeSinceLastRefill / refillIntervalNanos
        nextLastRefillTimeNanos = effectiveTimeForTokens
      }
      val tokensAfterRefill = (currentTokens + newTokensFromRefill).coerceAtMost(bucketSize)

      // 3. Check token availability
      if (tokensAfterRefill < 1.0) {
        return false // Would need to wait for tokens
      }

      // If we reach here, a permit can be acquired immediately.
      val finalTokensInBucket = tokensAfterRefill - 1.0
      // Acquired at evaluationTimeNanos
      val newProposedState = RateLimiterState(
        tokens = finalTokensInBucket,
        lastRefillNanos = nextLastRefillTimeNanos,
        lastAcquireCompletionNanos = evaluationTimeNanos
      )

      if (state.compareAndSet(currentState, newProposedState)) {
        return true // Successfully acquired
      }
      // CAS failed, another thread updated the state. Loop to re-evaluate and retry CAS.
    }
  }
}