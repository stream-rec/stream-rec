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
import github.hua0512.plugins.douyin.download.DouyinExtractor.Companion.SDK_VERSION
import github.hua0512.plugins.download.COMMON_USER_AGENT
import github.hua0512.plugins.jsEngine
import github.hua0512.utils.mainLogger
import github.hua0512.utils.toMD5Hex


/**
 * Douyin webmssdk SDK JS
 * @author hua0512
 * @date : 2024/6/21 12:58
 */
private var SDK_JS: String = ""

/**
 * Load webmssdk JS file content
 * @return webmssdk JS file content
 */
internal fun loadWebmssdk(): String {
  if (SDK_JS.isNotEmpty()) return SDK_JS
  synchronized(SDK_JS) {
    DouyinExtractor::class.java.getResourceAsStream("/douyin-webmssdk.js")?.bufferedReader()?.use {
      SDK_JS = it.readText()
    } ?: throw InvalidExtractionInitializationException("Failed to load douyin webmssdk")

  }
  return SDK_JS
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
internal fun getSignature(roomId: String, userId: String? = DouyinExtractor.USER_ID): String {
  assert(SDK_JS.isNotEmpty()) { "SDK_JS is empty" }

  // load JS
  jsEngine.eval(signatureJS)

  // build signature param
  val sigParam =
    "live_id=1,aid=6383,version_code=180800,webcast_sdk_version=$SDK_VERSION,room_id=$roomId,sub_room_id=,sub_channel_id=,did_rule=3,user_unique_id=$userId,device_platform=web,device_type=,ac=,identity=audience"
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
