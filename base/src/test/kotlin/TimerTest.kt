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

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test

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

/**
 * @author hua0512
 * @date : 2024/10/1 21:37

 */

class TimerTest {


  @Test
  fun testCurrentTime() {
    val definedStartTime = "00:00:00"
    val definedStopTime = "23:15:00"
    val currentTime =
      kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    val (startHour, startMin, startSec) = definedStartTime.split(":").map { it.toInt() }
    val (endHour, endMin, endSec) = definedStopTime.split(":").map { it.toInt() }
    var jStartTime = currentTime.withHour(startHour).withMinute(startMin).withSecond(startSec)
    var jEndTime = jStartTime.withHour(endHour).withMinute(endMin).withSecond(endSec)
      .let { if (endHour < startHour) it.plusDays(1) else it }
    // same day
    if (currentTime.isBefore(jStartTime)) {
      val delay = java.time.Duration.between(currentTime, jStartTime)
      val millis = delay.toMillis()
      println("before start time, waiting for $millis ms")
    } else if (currentTime.isAfter(jEndTime)) {
      // delay to wait for the next run, which should be the next day start time
      jStartTime = jStartTime.plusDays(1)
      jEndTime = jEndTime.plusDays(1)
      val delay = java.time.Duration.between(currentTime, jEndTime)
      val millis = delay.toMillis()
      println("end time passed, waiting for $millis ms")
    } else if (currentTime.isAfter(jStartTime) && currentTime.isBefore(jEndTime)) {
      val duration = java.time.Duration.between(currentTime, jEndTime)
      val millis = duration.toMillis()
      println("stopping download after $millis ms")
    } else {
      println("outside timer range")
    }
  }

}