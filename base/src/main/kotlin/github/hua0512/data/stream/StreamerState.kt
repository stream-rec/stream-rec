package github.hua0512.data.stream

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StreamerStateSerializer::class)
enum class StreamerState(val value: Int) {
  NOT_LIVE(0),
  LIVE(1),
  OUT_OF_SCHEDULE(2),
  INSPECTING_LIVE(3),
  FATAL_ERROR(4),
  CANCELLED(5),
  NOT_FOUND(6),
  NO_SPACE(7),
  UNKNOWN(99);

  companion object {
    fun valueOf(value: Int): StreamerState? = StreamerState.entries.find { it.value == value }
  }
}


object StreamerStateSerializer : KSerializer<StreamerState> {
  override val descriptor = PrimitiveSerialDescriptor("StreamerState", PrimitiveKind.INT)

  override fun deserialize(decoder: Decoder): StreamerState {
    return StreamerState.valueOf(decoder.decodeInt()) ?: StreamerState.UNKNOWN
  }

  override fun serialize(encoder: Encoder, value: StreamerState) {
    encoder.encodeInt(value.value)
  }
}