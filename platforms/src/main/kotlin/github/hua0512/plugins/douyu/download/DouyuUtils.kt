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

package github.hua0512.plugins.douyu.download

import com.github.michaelbull.result.*
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.douyu.download.DouyuExtractor.Companion.logger
import github.hua0512.plugins.jsEngine
import github.hua0512.utils.generateRandomString
import github.hua0512.utils.mapError
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*
import kotlin.collections.set

/**
 * @author hua0512
 * @date : 2024/3/22 14:01
 */

// MD5 JS Encryption
var MD5CRYPT = ""

@Synchronized
internal fun getMd5Crypt(): Result<String, ExtractorError> {
  if (MD5CRYPT.isNotEmpty()) return Ok(MD5CRYPT)

  // get from resources
  return runCatching {
    DouyuExtractor::class.java.getResourceAsStream("/crypto-js-md5.min.js")?.bufferedReader()?.readText()
      ?: throw IllegalStateException("Failed to read crypto-js-md5.min.js")
  }.andThen {
    MD5CRYPT = it
    Ok(it)
  }.mapError {
    ExtractorError.InitializationError(it)
  }
}

private const val vdwdae325wRegex = """(var vdwdae325w_64we =[\s\S]+?)\s*</script>"""

internal suspend fun HttpClient.getDouyuH5Enc(json: Json, body: String, rid: String): Result<String, ExtractorError> {
  assert(body.isNotEmpty())
  assert(rid.isNotEmpty())
  var jsEnc = Regex(vdwdae325wRegex).find(body)?.groupValues?.get(1)
  if (jsEnc == null || jsEnc.contains("ub98484234(").not()) {
    val result = runCatching {
      get("https://www.douyu.com/swf_api/homeH5Enc") {
        parameter("rids", rid)
        header(HttpHeaders.Referrer, DouyuExtractor.DOUYU_URL)
        contentType(ContentType.Application.Json)
      }
    }.mapError()

    if (result.isErr) return result.asErr()

    val json = json.parseToJsonElement(result.value.bodyAsText())

    val error = json.jsonObject["error"]?.jsonPrimitive?.intOrNull ?: return Err(ExtractorError.InvalidResponse("Failed to get douyu h5 enc"))
    if (error != 0) {
      return Err(ExtractorError.InvalidResponse("Failed to get douyu h5 enc due to error code $error"))
    }
    jsEnc = json.jsonObject["data"]?.jsonObject?.get(rid)?.jsonPrimitive?.content
      ?: return Err(ExtractorError.InvalidResponse("Failed to get douyu h5 enc for rid $rid"))
  }
  return Ok(jsEnc)
}


internal fun ub98484234(jsEnc: String, rid: String) = runCatching {
  assert(MD5CRYPT.isNotEmpty())
  val workFlow = Regex("""function ub98484234\(.+?\Weval\((\w+)\);""").find(jsEnc)?.groupValues?.get(1)
  val namesDict = mapOf(
    "debugMessages" to generateRandomString(8, true),
    "decryptedCodes" to generateRandomString(8, true),
    "patchCode" to generateRandomString(8),
    "resoult" to generateRandomString(8),
    "_ub98484234" to generateRandomString(8),
    "workflow" to workFlow
  )
  val jsDom = """
    ${namesDict["debugMessages"]} = {${namesDict["decryptedCodes"]}: []};
    if (!this.window) {window = {};}
    if (!this.document) {document = {};}
"""
  logger.trace("jsDom: $jsDom")
  val jsPatch = listOf(
    """
    function ${namesDict["patchCode"]}(workflow) {
        let testVari = /(\w+)=(\w+)\([\w\+]+\);.*?(\w+)="\w+";/.exec(workflow);
        if (testVari && testVari[1] == testVari[2]) {
            workflow += `${'$'}{testVari[1]}[${'$'}{testVari[3]}] = function() {return true;};`;
        }
        let subWorkflow = /(?:\w+=)?eval\((\w+)\)/.exec(workflow);
        if (subWorkflow) {
            let subPatch = `
                ${namesDict["debugMessages"]}.${namesDict["decryptedCodes"]}.push('sub workflow: ' + subWorkflow);
                subWorkflow = ${namesDict["patchCode"]}(subWorkflow);
            `.replace(/subWorkflow/g, subWorkflow[1]) + subWorkflow[0];
            workflow = workflow.replace(subWorkflow[0], subPatch);
        }
        return workflow;
    }
""", """
    ${namesDict["debugMessages"]}.${namesDict["decryptedCodes"]}.push(${namesDict["workflow"]});
    eval(${namesDict["patchCode"]}(${namesDict["workflow"]}));
"""
  )

  logger.trace("jsPatch: $jsPatch")

  val jsDebug = """
    var ${namesDict["_ub98484234"]} = ub98484234;
    ub98484234 = function(p1, p2, p3) {
        try {
            var resoult = ${namesDict["_ub98484234"]}(p1, p2, p3);
            ${namesDict["debugMessages"]}.${namesDict["resoult"]} = resoult;
        } catch(e) {
            ${namesDict["debugMessages"]}.${namesDict["resoult"]} = e.message;
        }
        return ${namesDict["debugMessages"]};
    };
"""

  val jsBuilder = StringBuilder().apply {
    appendLine(MD5CRYPT)
    appendLine(jsDom)
    namesDict["workflow"]?.let {
      appendLine(jsPatch[0])
      appendLine(jsEnc.replace("eval($it);", jsPatch[1]))
    } ?: appendLine(jsEnc)
    append(jsDebug)
  }
  // load js
  logger.trace(jsBuilder.toString())
  jsEngine.eval(jsBuilder.toString())

  val did = getRandomUuidHex()
  val tt = (kotlin.time.Clock.System.now().epochSeconds).toString()

  // eval function
  val result = jsEngine.eval("ub98484234('$rid', '$did', '$tt')") as Map<String, Any>

  val ub98484234 = mutableMapOf<String, Any>().apply {
    this["decryptedCodes"] = result[namesDict["decryptedCodes"]] ?: emptyArray<Any>()
    this["resoult"] = result[namesDict["resoult"]]!!
  }
  logger.debug("ub98484234: {}", ub98484234)
  val ub98484234String = ub98484234["resoult"] as String

  // result map
  mapOf(
    "v" to Regex("v=(\\d+)").find(ub98484234String)?.groupValues?.get(1),
    "did" to did,
    "tt" to tt,
    "sign" to Regex("sign=(\\w{32})").find(ub98484234String)?.groupValues?.get(1)
  )
}.mapError {
  ExtractorError.JsEngineError(it)
}


private fun getRandomUuidHex(): String {
  val uuid: UUID = UUID.randomUUID()
  return uuid.toString().replace("-", "")
}


internal fun extractDouyunRidFromUrl(url: String): String? {
  val queryParams = parseQueryString(url.substringAfter("?", ""))
  return if (queryParams == Parameters.Empty) // no query params, find rid from url
    Regex("""douyu\.com/(\d+)""").find(url)?.groupValues?.get(1)
  else // extract rid from url param
    queryParams["rid"]
}