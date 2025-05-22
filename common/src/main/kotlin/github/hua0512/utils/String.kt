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

package github.hua0512.utils

import java.security.MessageDigest
import java.util.*


/**
 * A regex pattern to match invalid characters in file names.
 */
private val fileNameRegex by lazy {
  Regex("[/\n\r\t\u0000\u000c`?*\\\\<>|\":']")
}

/**
 * @author hua0512
 * @date : 2024/10/10 22:02
 */

/**
 * Format the file name to a friendly file name
 * Replaces characters that are not allowed in file names with an underscore.
 *
 * Replaces the following characters with an underscore:
 * - / (forward slash)
 * - \ (backslash)
 * - \n (newline)
 * - \r (carriage return)
 * - \t (tab)
 * - \u0000 (null character)
 * - \u000c (form feed)
 * - ` (backtick)
 * - ? (question mark)
 * - * (asterisk)
 * - < (less than)
 * - > (greater than)
 * - | (pipe)
 * - " (double quote)
 * - ' (single quote)
 *
 * @receiver the filename to be formatted
 * @return a formatted filename
 */
fun String.formatToFileNameFriendly(): String = replace(fileNameRegex, "_")


/**
 * Extension function for the String class to decode a base64-encoded string.
 * @return The decoded string.
 */
fun String.decodeBase64(): String = String(Base64.getDecoder().decode(this))

fun String.md5(): String {
  val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
  return bytes.joinToString("") { "%02x".format(it) }
}


/**
 * Extension function for the String class to check if the string is empty.
 *
 * @return The original string if it's not empty, or null if it is.
 */
fun String.nonEmptyOrNull(): String? = ifEmpty { null }


/**
 * Generates a random string of specified length.
 *
 * @param length The length of the string to be generated.
 * @param noNumeric A flag to indicate whether the string should not contain numeric characters. Defaults to true.
 * @param noUpperLetters A flag to indicate whether the string should not contain upper-case letters. Defaults to false.
 * @return A random string of the specified length.
 */
fun generateRandomString(
  length: Int,
  noNumeric: Boolean = true,
  noUpperLetters: Boolean = false,
  lastChar: Char = 'z',
  additionalLetters: Array<Char> = emptyArray(),
): String {
  val allowedChars = buildList {
    addAll('a'..lastChar)
    if (!noNumeric) addAll('0'..'9')
    if (!noUpperLetters) addAll('A'..'Z')
    if (additionalLetters.isNotEmpty()) addAll(additionalLetters)
  }
  return (1..length)
    .map { allowedChars.random() }
    .joinToString("")
}