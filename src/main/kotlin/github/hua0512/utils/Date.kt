package github.hua0512.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * @author hua0512
 * @date : 2024/2/11 1:56
 */

fun toLocalDateTime(time: Long, pattern: String? = null): String {
  return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), TimeZone.getDefault().toZoneId()).run {
    if (pattern?.isEmpty()!!) {
      this.toString()
    } else {
      this.format(DateTimeFormatter.ofPattern(pattern))
    }
  }
}