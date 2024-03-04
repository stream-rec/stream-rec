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

package github.hua0512.data.stats

import github.hua0512.utils.StatsEntity
import kotlinx.serialization.Serializable

/**
 * Stats data class
 * @author hua0512
 * @date : 2024/3/4 10:27
 */
@Serializable
data class Stats(
  val timeStamp: Long,
  val streams: Int,
  val uploads: Int,
  val failedUploads: Int,
) {

  var id = 0L

  constructor(statsEntity: StatsEntity) : this(
    statsEntity.time,
    statsEntity.totalStreams.toInt(),
    statsEntity.totalUploads.toInt(),
    statsEntity.totalFailedUploads.toInt()
  ) {
    id = statsEntity.id
  }


  fun toEntity(): StatsEntity {
    return StatsEntity(id, timeStamp, streams.toLong(), uploads.toLong(), failedUploads.toLong())
  }
}