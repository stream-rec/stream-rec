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

@file:Suppress("NOTHING_TO_INLINE")

package github.hua0512.utils

import github.hua0512.plugins.StreamerContext
import org.slf4j.Logger

/**
 * Streamer logging context
 * @author hua0512
 * @date : 2024/9/26 22:52
 */

interface StreamerLoggerContext {

  val context: StreamerContext

  val logger: Logger
}

inline fun StreamerLoggerContext.trace(content: String, vararg objs: Any?) = logger.trace(content, *objs)

inline fun StreamerLoggerContext.debug(content: String, vararg objs: Any?, throwable: Throwable? = null) =
  logger.debug("${context.name} $content", *objs, throwable)

inline fun StreamerLoggerContext.debug(content: String) = debug(content, null)


inline fun StreamerLoggerContext.info(content: String, vararg objs: Any?, throwable: Throwable? = null) =
  logger.info("${context.name} $content", *objs, throwable)

inline fun StreamerLoggerContext.info(content: String) = info(content, null)

inline fun StreamerLoggerContext.error(content: String, vararg objs: Any?, throwable: Throwable? = null) =
  logger.error("${context.name} $content", *objs, throwable)

inline fun StreamerLoggerContext.warn(content: String, vararg objs: Any?, throwable: Throwable? = null) =
  logger.warn("${context.name} $content", *objs, throwable)