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

package github.hua0512.data.stream

import github.hua0512.data.stream.entity.StreamDataEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class StreamData(
  var id: Long = 0,
  val title: String,
  val dateStart: Long? = null,
  val dateEnd: Long? = null,
  val outputFilePath: String,
  val danmuFilePath: String? = null,
  val outputFileSize: Long = 0,
  val streamerId: Long = 0,
  @Transient
  val streamer: Streamer? = null,
) {

  var streamerName: String = ""
    get() = streamer?.name ?: field

  var platform: String = ""
    get() = streamer?.platform?.name ?: field

  constructor(entity: StreamDataEntity, streamer: Streamer? = null) : this(
    entity.id,
    entity.title,
    entity.dateStart,
    entity.dateEnd,
    entity.outputFilePath,
    entity.danmuFilePath,
    entity.outputFileSize,
    entity.streamerId,
    streamer,
  )

  fun toEntity() = StreamDataEntity(
    id = id,
    title = title,
    dateStart = dateStart,
    dateEnd = dateEnd,
    outputFilePath = outputFilePath,
    danmuFilePath = danmuFilePath,
    outputFileSize = outputFileSize,
    streamerId = streamerId,
  )
}