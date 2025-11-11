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

package github.hua0512.plugins.douyin.download

import com.github.michaelbull.result.*
import github.hua0512.app.COMMON_USER_AGENT
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.AID_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.AID_VALUE
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.ROOM_ID_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.SDK_VERSION
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.USER_UNIQUE_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.VERSION_CODE_KEY
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.VERSION_CODE_VALUE
import github.hua0512.plugins.douyin.download.DouyinRequestParams.Companion.WEBCAST_SDK_VERSION_KEY
import github.hua0512.plugins.jsEngine
import github.hua0512.utils.logger
import github.hua0512.utils.toMD5Hex
import kotlinx.atomicfu.atomic


private val logger by lazy { logger(DouyinExtractor::class.java) }

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
internal fun loadWebmssdk(): Result<String, ExtractorError> {
  return SDK_JS.value?.let { Ok(it) } ?: run {
    val sdkText = DouyinExtractor::class.java.getResourceAsStream("/douyin-webmssdk.js")?.bufferedReader()?.use {
      it.readText()
    } ?: return Err(ExtractorError.InitializationError(IllegalStateException("failed to load douyin-webmssdk.js")))
    val status = SDK_JS.compareAndSet(null, sdkText)
    if (!status) {
      logger.error("failed to set douyin webmssdk")
    }
    Ok(SDK_JS.value ?: sdkText)
  }
}

private val signatureJS by lazy {
  // add dom element
  val jsDom = """
    document = {};
    window = {};
    Request = {};
    Headers = {};
    navigator = {
      userAgent: '$COMMON_USER_AGENT'
    };
    window.innerHeight = 910;
    window.innerWidth = 1920;
    window.outerHeight = 28;
    window.outerWidth = 160;
    window.screenX = 0;
    window.screenY = 9;
    window.pageYOffset = 0;
    window.pageXOffset = 0;
    window.screen = {}
    window.onwheelx = {"_Ax": "0X21"}
    window.navigator = navigator
    window.navigator.cookieEnabled = true;
    window.navigator.platform = "Win32";
    window.navigator.language = "zh-CN";
    window.navigator.appCodeName = "${COMMON_USER_AGENT.substringBefore('/')}";
    window.navigator.appVersion = "$COMMON_USER_AGENT";
    window.navigator.onLine = true;
    window.addEventListener = function() {};
    window.sessionStorage = {}
    window.localStorage = {}
    document.hidden = true;
    document.webkitHidden = true;
    document.cookie = '';
  """.trimIndent()

  val loadResult = loadWebmssdk()
  if (loadResult.isErr) {
    return@lazy ""
  }

  // final JS
  jsDom + loadResult.get()!!
}

/**
 * Get a signature for a specific live room
 * @param roomId live room id
 * @param userId user id
 * @return signature string
 */
internal fun getSignature(roomId: String, userId: String): Result<String, ExtractorError> {
  assert(!(SDK_JS.value.isNullOrEmpty())) { "SDK_JS is empty" }

  if (signatureJS.isEmpty()) {
    return Err(ExtractorError.JsEngineError(IllegalStateException("failed to load douyin-webmssdk.js")))
  }

  // load JS
  jsEngine.eval(signatureJS)

  // build signature param
  val sigParam =
    "live_id=1,${AID_KEY}=${AID_VALUE},${VERSION_CODE_KEY}=${VERSION_CODE_VALUE},${WEBCAST_SDK_VERSION_KEY}=$SDK_VERSION,${ROOM_ID_KEY}=$roomId,sub_room_id=,sub_channel_id=,did_rule=3,${USER_UNIQUE_KEY}=$userId,device_platform=web,device_type=,ac=,identity=audience"
  logger.debug("SigParam: {}", sigParam)
  // get MD5 of sigParam
  val md5SigParam = sigParam.toByteArray().toMD5Hex()
  logger.debug("MD5 sigParam: {}", md5SigParam)
  // build function caller
  val functionCaller = "get_sign('${md5SigParam}')"
  // call JS function
  return runCatching {
    (jsEngine.eval(functionCaller) as String).also {
      logger.debug("Signature: {}", it)
    }
  }.mapError {
    ExtractorError.JsEngineError(it)
  }
}
