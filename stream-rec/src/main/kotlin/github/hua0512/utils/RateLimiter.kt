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

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A token bucket rate limiter implementation
 * More accurate and efficient than simple time-based limiting
 */
class RateLimiter(
  private val permitsPerSecond: Long,
  private var _minDelayMs: Long,
  private val maxBurst: Int = 1,
) {
  private val bucketSize = maxBurst.coerceAtLeast(1)
  private val refillInterval = (1000.0 / permitsPerSecond).milliseconds

  private val lastRefillTime = atomic(System.nanoTime())
  private val availableTokens = atomic(bucketSize.toDouble())
  private val mutex = Mutex()

  private var isFirstRequest = atomic(true)

  /**
   * Minimum delay between permits in milliseconds
   * Can be updated dynamically
   */
  var minDelayMs: Long
    get() = _minDelayMs
    private set(value) {
      _minDelayMs = value
    }

  /**
   * Update the minimum delay between permits
   * @param value new minimum delay in milliseconds
   */
  suspend fun updateMinDelay(value: Long) = mutex.withLock {
    _minDelayMs = value
    isFirstRequest.update { true }
  }

  /**
   * Acquires a permit from the rate limiter, suspending if necessary
   * @return the delay applied, if any
   */
  suspend fun acquire(): Duration = mutex.withLock {
    // Fast path for first request
    if (isFirstRequest.compareAndSet(expect = true, update = false)) {
      availableTokens.value -= 1.0
      return 0.milliseconds
    }

    val now = System.nanoTime()
    val timeSinceLastRefill = (now - lastRefillTime.value).nanoseconds
    
    // Always calculate the minimum required delay
    val minRequired = minDelayMs.milliseconds
    
    // If minimum delay hasn't elapsed, wait
    if (timeSinceLastRefill < minRequired) {
      val waitTime = minRequired - timeSinceLastRefill
      delay(waitTime)
      return waitTime
    }

    // Normal token bucket logic
    if (timeSinceLastRefill >= refillInterval) {
      val newTokens = (timeSinceLastRefill / refillInterval).toDouble()
      val currentTokens = availableTokens.value
      val updatedTokens = (currentTokens + newTokens).coerceAtMost(bucketSize.toDouble())
      
      availableTokens.value = updatedTokens
      lastRefillTime.value = now
    }

    val requiredDelay = if (availableTokens.value >= 1.0) {
      availableTokens.value -= 1.0
      0.milliseconds
    } else {
      val timeToNextToken = refillInterval * (1.0 - availableTokens.value)
      timeToNextToken.coerceAtLeast(minRequired)
    }

    if (requiredDelay > 0.milliseconds) {
      delay(requiredDelay)
    }

    requiredDelay
  }

  /**
   * Try to acquire a permit without waiting
   * @return true if permit was acquired, false if would need to wait
   */
  suspend fun tryAcquire(): Boolean = mutex.withLock {
    val now = System.nanoTime()
    val timeSinceLastRefill = (now - lastRefillTime.value).nanoseconds

    if (timeSinceLastRefill >= refillInterval) {
      val newTokens = (timeSinceLastRefill / refillInterval).toDouble()
      val currentTokens = availableTokens.value
      val updatedTokens = (currentTokens + newTokens).coerceAtMost(bucketSize.toDouble())

      availableTokens.value = updatedTokens
      lastRefillTime.value = now
    }

    if (availableTokens.value >= 1.0) {
      availableTokens.value -= 1.0
      true
    } else {
      false
    }
  }
}