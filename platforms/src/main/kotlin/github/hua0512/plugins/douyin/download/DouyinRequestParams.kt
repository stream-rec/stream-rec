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

package github.hua0512.plugins.douyin.download

import github.hua0512.plugins.download.COMMON_USER_AGENT


internal typealias DouyinParams = DouyinRequestParams.Companion

/**
 * Douyin request parameters
 * @author hua0512
 * @date : 2024/10/6 21:24
 */
internal class DouyinRequestParams {

  companion object {
    internal const val AID_KEY = "aid"
    internal const val AID_VALUE = "6383"
    internal const val VERSION_CODE_KEY = "version_code"
    internal const val VERSION_CODE_VALUE = "180800"
    internal const val WEBCAST_SDK_VERSION_KEY = "webcast_sdk_version"
    internal const val UPDATE_VERSION_CODE_KEY = "update_version_code"

    internal const val SDK_VERSION = "1.0.14-beta.0"

    internal const val ROOM_ID_KEY = "room_id"
    internal const val WEB_RID_KEY = "web_rid"
    internal const val USER_UNIQUE_KEY = "user_unique_id"
    internal const val SIGNATURE_KEY = "signature"

    /**
     * A map of common parameters used for making requests to the Douyin API.
     *
     * The map contains the following key-value pairs:
     * - "aid" - The Douyin application ID
     * - "device_platform" - The platform of the device making the request (e.g., "web")
     * - "browser_language" - The language of the browser making the request (e.g., "zh-CN")
     * - "browser_platform" - The platform of the browser making the request (e.g., "Win32")
     * - "browser_name" - The name of the browser making the request (e.g., "Chrome")
     * - "browser_version" - The version of the browser making the request (e.g., "98.0.4758.102")
     * - "compress" - The compression method used for the response (e.g., "gzip")
     * - "signature" - The signature for the request
     * - "heartbeatDuration" - The duration of the heartbeat in milliseconds
     */
    internal val commonParams = mapOf(
      "app_name" to "douyin_web",
      VERSION_CODE_KEY to VERSION_CODE_VALUE,
      WEBCAST_SDK_VERSION_KEY to SDK_VERSION,
      UPDATE_VERSION_CODE_KEY to SDK_VERSION,
      "compress" to "gzip",
      "device_platform" to "web",
      "browser_language" to "zh-CN",
      "browser_platform" to "Win32",
      "browser_name" to "Mozilla",
      "browser_version" to COMMON_USER_AGENT.removePrefix("Mozilla/").trim(),
      "host" to "https://live.douyin.com",
      AID_KEY to AID_VALUE,
      "live_id" to "1",
      "did_rule" to "3",
      "endpoint" to "live_pc",
      "identity" to "audience",
      "heartbeatDuration" to "0",
    )
  }
}