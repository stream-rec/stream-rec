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

package github.hua0512.plugins.base

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * The error class for the extractors.
 * @author hua0512
 * @date : 2024/11/18 21:54
 */
@Serializable
sealed class ExtractorError {
  @Serializable
  data object InvalidExtractionUrl : ExtractorError()

  @Serializable
  data class InitializationError(@Contextual val throwable: Throwable) : ExtractorError()

  @Serializable
  data class ApiError(@Contextual val throwable: Throwable) : ExtractorError()

  @Serializable
  data class InvalidResponse(val message: String) : ExtractorError()

  @Serializable
  data class JsEngineError(@Contextual val throwable: Throwable) : ExtractorError()

  @Serializable
  data object StreamerNotFound : ExtractorError()

  @Serializable
  data object StreamerBanned : ExtractorError()

  @Serializable
  data object NoStreamsFound : ExtractorError()

  @Serializable
  data class FallbackError(@Contextual val throwable: Throwable) : ExtractorError()

}