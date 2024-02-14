import org.junit.Test

class CookiesExtractor {

  @Test
  fun extractCookiesFromString() {
    val cookies =
      "cookies"
    val acNoncePattern = "__ac_nonce=([^;]*)".toRegex()
    val acNonceString = acNoncePattern.find(cookies)?.groupValues?.get(1) ?: ""
    val ttwidPattern = "ttwid=([^;]*)".toRegex()
    val ttwidString = ttwidPattern.find(cookies)?.groupValues?.get(1) ?: ""
    val acSignPattern = "__ac_signature=([^;]*)".toRegex()
    val acSignString = acSignPattern.find(cookies)?.groupValues?.get(1) ?: ""
    val msTokenPattern = "msToken=([^;]*)".toRegex()
    val msTokenString = msTokenPattern.find(cookies)?.groupValues?.get(1) ?: ""
    val sessionIdPattern = "sessionId=([^;]*)".toRegex()
    val sessionIdString = sessionIdPattern.find(cookies)?.groupValues?.get(1) ?: ""
    var final = ""
    if (ttwidString.isNotEmpty()) {
      final += "ttwid=$ttwidString; "
    }
    if (msTokenString.isNotEmpty()) {
      final += "msToken=$msTokenString; "
    }
    if (acNonceString.isNotEmpty()) {
      final += "__ac_nonce=$acNonceString; "
    }
    if (acSignString.isNotEmpty()) {
      final += "__ac_signature=$acSignString; "
    }
    if (sessionIdString.isNotEmpty()) {
      final += "sessionId=$sessionIdString; "
    }
    println(final)
  }
}