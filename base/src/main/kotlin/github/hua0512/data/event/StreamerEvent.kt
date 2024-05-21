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

package github.hua0512.data.event

import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.StreamingPlatform
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


/**
 * StreamerEvent is a sealed class that represents events related to streamers.
 */
@Serializable
sealed class StreamerEvent : Event {

  /**
   * The name of the stream.
   */
  abstract val streamer: String

  /**
   * The URL of the stream.
   */
  abstract val streamerUrl: String

  /**
   * The platform of the stream.
   */
  abstract val streamerPlatform: StreamingPlatform

  @Serializable
  data class StreamerOnline(
    override val streamer: String,
    override val streamerUrl: String,
    override val streamerPlatform: StreamingPlatform,
    val streamTitle: String,
    val time: Instant,
  ) : StreamerEvent()

  /**
   * StreamerRecordStop represents a stream record stop event.
   * @property streamer The name of the stream.
   * @property streamerUrl The URL of the stream.
   * @property streamerPlatform The platform of the stream.
   * @property time The time when the record stopped.
   */
  @Serializable
  data class StreamerRecordStop(
    override val streamer: String,
    override val streamerUrl: String,
    override val streamerPlatform: StreamingPlatform,
    val time: Instant,
    @Serializable(with = ExceptionSerializer::class)
    val reason: Exception?,
  ) : StreamerEvent()

  @Serializable
  data class StreamerOffline(
    override val streamer: String,
    override val streamerUrl: String,
    override val streamerPlatform: StreamingPlatform,
    val time: Instant,
    val data: List<StreamData>? = emptyList(),
  ) : StreamerEvent()

  /**
   * StreamerException represents a stream exception event.
   * @property streamer The name of the stream.
   * @property streamerUrl The URL of the stream.
   * @property streamerPlatform The platform of the stream.
   * @property time The time when the exception occurred.
   * @property exception The exception that occurred.
   */
  @Serializable
  data class StreamerException(
    override val streamer: String,
    override val streamerUrl: String,
    override val streamerPlatform: StreamingPlatform,
    val time: Instant,
    @Serializable(with = ExceptionSerializer::class)
    val exception: Exception,
  ) : StreamerEvent()

}


internal object ExceptionSerializer : KSerializer<Exception> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Exception", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): Exception {
    return Exception(decoder.decodeString())
  }

  override fun serialize(encoder: Encoder, value: Exception) {
    encoder.encodeString(value.message ?: "")
  }
}