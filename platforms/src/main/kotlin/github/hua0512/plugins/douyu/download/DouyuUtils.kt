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

package github.hua0512.plugins.douyu.download

import github.hua0512.plugins.douyu.download.DouyuExtractor.Companion.logger
import github.hua0512.utils.generateRandomString
import github.hua0512.utils.withIOContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*
import javax.script.ScriptEngineManager

/**
 * @author hua0512
 * @date : 2024/3/22 14:01
 */

// MD5 JS Encryption
var MD5CRYPT = ""


suspend fun getMd5Crypt(client: HttpClient): String {
  if (MD5CRYPT.isNotEmpty()) return MD5CRYPT

  // get from resources
  MD5CRYPT = withIOContext { DouyuExtractor::class.java.getResource("crypto-js-md5.min.js")?.readText() }?.let {
    return it
  } ?: ""

  val url = "https://cdnjs.cloudflare.com/ajax/libs/crypto-js/3.1.9-1/crypto-js.min.js"
  val response = client.get(url)
  if (response.status.value != 200) {
    throw IllegalStateException("Failed to get MD5CRYPT")
  }
  MD5CRYPT = response.bodyAsText()
  return MD5CRYPT
}

private const val vdwdae325wRegex = """(var vdwdae325w_64we =[\s\S]+?)\s*</script>"""

internal suspend fun HttpClient.getDouyuH5Enc(json: Json, body: String, rid: String): String {
  assert(body.isNotEmpty())
  assert(rid.isNotEmpty())
  var jsEnc = Regex(vdwdae325wRegex).find(body)?.groupValues?.get(1)
  if (jsEnc == null || jsEnc.contains("ub98484234(").not()) {
    val data = get("https://www.douyu.com/swf_api/homeH5Enc") {
      parameter("rids", rid)
      header(HttpHeaders.Referrer, DouyuExtractor.DOUYU_URL)
    }
    if (data.status != HttpStatusCode.OK) {
      throw IllegalStateException("Failed to get douyu h5 enc due to status code ${data.status}")
    }
    val dataBody = data.bodyAsText()
    val jsonText = json.parseToJsonElement(dataBody)
    val error = jsonText.jsonObject["error"]?.jsonPrimitive?.intOrNull ?: throw IllegalStateException("Failed to get douyu h5 enc")
    if (error != 0) {
      throw IllegalStateException("Failed to get douyu h5 enc due to error code $error")
    }
    jsEnc =
      jsonText.jsonObject["data"]?.jsonObject?.get(rid)?.jsonPrimitive?.content ?: throw IllegalStateException("Failed to get douyu h5 enc data")
  }
  return jsEnc
}


internal fun ub98484234(jsEnc: String, rid: String): Map<String, Any?> {
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
  val jsEngineManager = ScriptEngineManager()
  val jsEngine = jsEngineManager.getEngineByName("nashorn")
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
  try {
    logger.trace(jsBuilder.toString())
    jsEngine.eval(jsBuilder.toString())
  } catch (e: Exception) {
    throw IllegalStateException("Failed to load js", e)
  }

  val did = getRandomUuidHex()
  val tt = (Clock.System.now().epochSeconds).toString()

  // eval function
  val result = try {
    jsEngine.eval("ub98484234('$rid', '$did', '$tt')") as Map<String, Any>
  } catch (e: Exception) {
    throw IllegalStateException("Failed to eval js", e)
  }

  val ub98484234 = mutableMapOf<String, Any>().apply {
    this["decryptedCodes"] = result[namesDict["decryptedCodes"]] ?: emptyArray<Any>()
    this["resoult"] = result[namesDict["resoult"]]!!
  }
  logger.debug("ub98484234: {}", ub98484234)
  val ub98484234String = ub98484234["resoult"] as String
  return mapOf(
    "v" to Regex("v=(\\d+)").find(ub98484234String)?.groupValues?.get(1),
    "did" to did,
    "tt" to tt,
    "sign" to Regex("sign=(\\w{32})").find(ub98484234String)?.groupValues?.get(1)
  )
}


private fun getRandomUuidHex(): String {
  val uuid: UUID = UUID.randomUUID()
  return uuid.toString().replace("-", "")
}


fun extractDouyunRidFromUrl(url: String): String? {
  // extract rid from url param
  val matchResult = parseQueryString(url.substringAfter("?"))["rid"]
  return matchResult
}