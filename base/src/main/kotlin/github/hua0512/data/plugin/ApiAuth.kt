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

@file:OptIn(ExperimentalSerializationApi::class)

package github.hua0512.data.plugin

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable


/**
 * Authentication types for API uploads.
 */
@Serializable
sealed class ApiAuth {
  /**
   * Basic authentication with username and password.
   */
  @Serializable
  data class Basic(val username: String, val password: String) : ApiAuth()

  /**
   * Bearer token authentication.
   */
  @Serializable
  data class Bearer(val token: String) : ApiAuth()

  /**
   * API key authentication.
   */
  @Serializable
  data class ApiKey(val key: String, val headerName: String = "X-Api-Key") : ApiAuth()

  /**
   * OAuth authentication.
   */
  @Serializable
  data class OAuth(
    val tokenUrl: String,
    val clientId: String,
    val clientSecret: String,
    val scope: String? = null,
  ) : ApiAuth()
}
