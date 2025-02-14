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

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


private const val STREAMER_PLACEHOLDER = "{streamer}"
private const val TITLE_PLACEHOLDER = "{title}"
private const val PLATFORM_PLACEHOLDER = "{platform}"

private val placeholders = arrayOf(
  STREAMER_PLACEHOLDER,
  TITLE_PLACEHOLDER,
  PLATFORM_PLACEHOLDER,
  "%Y",
  "%m",
  "%d",
  "%H",
  "%M",
  "%S",
)

fun String.replacePlaceholders(streamer: String, title: String, platform: String? = null, time: Instant? = null): String {
  val localDateTime: LocalDateTime? = time?.toLocalDateTime(TimeZone.currentSystemDefault())
  val result = StringBuilder(this)

  for (placeholder in placeholders) {
    val value = when (placeholder) {
      STREAMER_PLACEHOLDER -> streamer
      TITLE_PLACEHOLDER -> title
      PLATFORM_PLACEHOLDER -> platform
      "%Y" -> localDateTime?.year?.toString()
      "%m" -> localDateTime?.monthNumber?.let { formatLeadingZero(it) }
      "%d" -> localDateTime?.dayOfMonth?.let { formatLeadingZero(it) }
      "%H" -> localDateTime?.hour?.let { formatLeadingZero(it) }
      "%M" -> localDateTime?.minute?.let { formatLeadingZero(it) }
      "%S" -> localDateTime?.second?.let { formatLeadingZero(it) }
      else -> null
    } ?: continue

    var index = result.indexOf(placeholder)
    while (index != -1) {
      result.replace(index, index + placeholder.length, value)
      index = result.indexOf(placeholder, index + value.length)
    }
  }
  return result.toString()
}

private fun formatLeadingZero(value: Int): String = String.format("%02d", value)

fun String.substringBeforePlaceholders(): String {
  if (this.isEmpty()) {
    return ""
  }
  val index = placeholders.fold(this.length) { acc, placeholder ->
    val placeholderIndex = this.indexOf(placeholder)
    if (placeholderIndex != -1 && placeholderIndex < acc) {
      placeholderIndex
    } else {
      acc
    }
  }
  return this.substring(0, index)
}