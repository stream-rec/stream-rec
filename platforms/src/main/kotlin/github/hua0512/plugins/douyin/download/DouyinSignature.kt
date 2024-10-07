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

import github.hua0512.plugins.base.exceptions.InvalidExtractionInitializationException
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.AID_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.AID_VALUE
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.ROOM_ID_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.SDK_VERSION
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.USER_UNIQUE_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.VERSION_CODE_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.VERSION_CODE_VALUE
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.WEBCAST_SDK_VERSION_KEY
import github.hua0512.plugins.douyu.download.DouyuExtractor.Companion.logger
import github.hua0512.plugins.download.COMMON_USER_AGENT
import github.hua0512.plugins.jsEngine
import github.hua0512.utils.mainLogger
import github.hua0512.utils.toMD5Hex
import kotlinx.atomicfu.atomic


/**
 * Douyin webmssdk SDK JS
 * @author hua0512
 * @date : 2024/6/21 12:58
 */
private var SDK_JS = atomic<String?>(null)

/**
 * Load webmssdk JS file content
 * @return webmssdk JS file content
 */
internal fun loadWebmssdk(): String = SDK_JS.value ?: run {
  val sdkText = DouyinExtractor::class.java.getResourceAsStream("/douyin-webmssdk.js")?.bufferedReader()?.use {
    it.readText()
  } ?: throw InvalidExtractionInitializationException("Failed to load douyin webmssdk")
  val status = SDK_JS.compareAndSet(null, sdkText)
  if (!status) {
    logger.error("failed to set douyin webmssdk")
  }
  SDK_JS.value ?: sdkText
}

private val signatureJS by lazy {
  // add dom element
  val jsDom = """
    document = {};
    window = {};
    navigator = {
      userAgent: '$COMMON_USER_AGENT'
    };
  """.trimIndent()

  // final JS
  jsDom + loadWebmssdk()
}

/**
 * Get a signature for a specific live room
 * @param roomId live room id
 * @param userId user id
 * @return signature string
 */
internal fun getSignature(roomId: String, userId: String): String {
  assert(!(SDK_JS.value.isNullOrEmpty())) { "SDK_JS is empty" }

  // load JS
  jsEngine.eval(signatureJS)

  // build signature param
  val sigParam =
    "live_id=1,${AID_KEY}=${AID_VALUE},${VERSION_CODE_KEY}=${VERSION_CODE_VALUE},${WEBCAST_SDK_VERSION_KEY}=$SDK_VERSION,${ROOM_ID_KEY}=$roomId,sub_room_id=,sub_channel_id=,did_rule=3,${USER_UNIQUE_KEY}=$userId,device_platform=web,device_type=,ac=,identity=audience"
  mainLogger.debug("SigParam: {}", sigParam)
  // get MD5 of sigParam
  val md5SigParam = sigParam.toByteArray().toMD5Hex()
  mainLogger.debug("MD5 sigParam: {}", md5SigParam)
  // build function caller
  val functionCaller = "get_sign('${md5SigParam}')"
  // call JS function
  return try {
    (jsEngine.eval(functionCaller) as String).also {
      mainLogger.debug("Signature: {}", it)
    }
  } catch (e: Exception) {
    throw InvalidExtractionInitializationException("Failed to get signature, error: ${e.message}")
  }
}
