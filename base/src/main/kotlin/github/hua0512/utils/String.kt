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

package github.hua0512.utils

import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import java.util.*


/**
 * Extension function for the String class to replace placeholders in the string with provided values.
 *
 * @param streamer The name of the streamer. This value will replace the "{streamer}" placeholder in the string.
 * @param title The title of the stream. This value will replace the "{title}" placeholder in the string.
 * @param time The time of the stream as an Instant object. The date and time parts of this value will replace the corresponding placeholders in the string. Defaults to the current system time if not provided.
 * @return The string with all placeholders replaced with their corresponding values.
 */
fun String.replacePlaceholders(streamer: String, title: String, time: Instant = Clock.System.now()): String {
  // Convert the Instant time to a LocalDateTime object in the system's default time zone
  val localDateTime: LocalDateTime = time.toLocalDateTime(TimeZone.currentSystemDefault())

  // Define a map of placeholders to their replacement values
  val toReplace: Map<String, String> = mapOf(
    "{streamer}" to streamer,
    "{title}" to title,
    "%yyyy" to localDateTime.year.toString(),
    "%MM" to formatLeadingZero(localDateTime.monthNumber),
    "%dd" to formatLeadingZero(localDateTime.dayOfMonth),
    "%HH" to formatLeadingZero(localDateTime.hour),
    "%mm" to formatLeadingZero(localDateTime.minute),
    "%ss" to formatLeadingZero(localDateTime.second),
  )

  // Replace each placeholder in the string with its corresponding value from the map and return the result
  return toReplace.entries.fold(this) { acc, entry ->
    acc.replace(entry.key, entry.value)
  }
}

/**
 * Formats an integer value to a string with a leading zero if the value is less than 10.
 * @param value The integer value to format.
 * @return The formatted string.
 */
private fun formatLeadingZero(value : Int) : String = String.format("%02d", value)


/**
 * Extension function for the String class to check if the string is empty.
 *
 * @return The original string if it's not empty, or null if it is.
 */
fun String.nonEmptyOrNull(): String? {
  return this.ifEmpty { null }
}


/**
 * Generates a random string of specified length.
 *
 * @param length The length of the string to be generated.
 * @param noNumeric A flag to indicate whether the string should not contain numeric characters. Defaults to true.
 * @return A random string of the specified length.
 */
fun generateRandomString(length: Int, noNumeric: Boolean = true): String {
  val allowedChars = ('A'..'Z') + ('a'..'z').run {
    if (noNumeric) this else this + ('0'..'9')
  }
  return (1..length)
    .map { allowedChars.random() }
    .joinToString("")
}

/**
 * Extension function for the String class to decode a base64-encoded string.
 * @return The decoded string.
 */
fun String.decodeBase64(): String {
  return String(Base64.getDecoder().decode(this))
}