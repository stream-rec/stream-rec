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

package github.hua0512.flv.data.amf

import github.hua0512.flv.data.other.FlvKeyframe
import github.hua0512.flv.utils.Keyframe
import kotlinx.io.Sink
import kotlinx.io.writeDouble
import kotlinx.io.writeUShort
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AMF0 data types
 * @author hua0512
 * @date : 2024/6/9 9:58
 * @see <a href="https://en.wikipedia.org/wiki/Action_Message_Format#AMF0">AMF0 spec</a>
 */
enum class Amf0Type(val byte: Byte) {
  NUMBER(0x00),
  BOOLEAN(0x01),
  STRING(0x02),
  OBJECT(0x03),

  /**
   * MovieClip is not supported (reserved)
   */
  MOVIE_CLIP(0x04),

  NULL(0x05),
  UNDEFINED(0x06),
  REFERENCE(0x07),
  ECMA_ARRAY(0x08),
  OBJECT_END(0x09),
  STRICT_ARRAY(0x0A),
  DATE(0x0B),
  LONG_STRING(0x0C),

  /**
   * Below formats are not likely to be used in FLV
   */
  XML_DOCUMENT(0x0F),
  TYPED_OBJECT(0x10),
  AMF3_OBJECT(0x11)
}


@Serializable
sealed interface AmfValue {

  fun write(sink: Sink)

  val size: Int

  /**
   * AMF0 data values
   * @author hua0512
   * @date : 2024/6/8 20:24
   */
  @Serializable
  sealed class Amf0Value(open val dataType: Byte) : AmfValue {

    override fun write(sink: Sink) {
      sink.writeByte(dataType)
    }


    /**
     * AMF0 Keyframes object
     *
     * This class represents a collection of keyframes in the AMF0 format.
     * It extends the `Amf0Value.Object` class and provides methods to manage keyframes.
     *
     * @property keyframes The list of keyframes.
     * @constructor Creates an instance of `Amf0Keyframes` with an optional initial list of keyframes.
     *
     * @param keyframes The initial list of keyframes.
     *
     * @see Amf0Value.Object
     * @see Keyframe
     *
     * @throws IllegalArgumentException if the number of keyframes exceeds the maximum limit.
     *
     * @date 2024/9/9 20:43
     */
    @Serializable
    @SerialName("keyframes")
    data class Amf0Keyframes(val keyframes: List<FlvKeyframe> = mutableListOf()) : Amf0Value(Amf0Type.OBJECT.byte) {

      companion object {
        private const val KEY_TIMES = "times"
        internal const val KEY_FILEPOSITIONS = "filepositions"
        internal const val KEY_SPACER = "spacer"

        internal const val MAX_KEYFRAMES_PERMITTED = 6300
        private const val MIN_INTERVAL_BETWEEN_KEYFRAMES = 1900
      }

      override val size: Int
        get() {
          // 1 byte type + 4 byte type marker
          var totalSize = 1
          properties.forEach { (key, value) ->
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            totalSize += 2 + keyBytes.size + value.size
          }
          // + 3 byte end marker
          totalSize += 3
          return totalSize
        }


      /**
       * The properties of the AMF0 Keyframes object.
       *
       * @return A map containing the keyframe times, file positions, and spacer values.
       */
      val properties: Map<kotlin.String, AmfValue.Amf0Value>
        get() = mapOf(
          KEY_TIMES to StrictArray(keyframes.map { Number(it.timestamp / 1000.0) }),
          KEY_FILEPOSITIONS to StrictArray(keyframes.map { Number(it.filePosition.toDouble()) }),
          KEY_SPACER to StrictArray(List((MAX_KEYFRAMES_PERMITTED - keyframes.size) * 2) { Number(Double.NaN) })
        )

      /**
       * The number of keyframes in the collection.
       */
      val keyframesCount = keyframes.size

      /**
       * Adds a keyframe to the collection.
       *
       * @param keyframe The keyframe to add.
       * @throws IllegalArgumentException if the number of keyframes exceeds the maximum limit.
       */
      fun addKeyframe(keyframe: Keyframe) {
        if (keyframes.size >= MAX_KEYFRAMES_PERMITTED) {
          throw IllegalArgumentException("Keyframes size exceeds the maximum limit of $MAX_KEYFRAMES_PERMITTED")
        }

        if (keyframes.isEmpty() || (keyframe.timestamp - keyframes.last().timestamp) >= MIN_INTERVAL_BETWEEN_KEYFRAMES) {
          keyframes as MutableList
          keyframes.add(keyframe)
        }
      }

      /**
       * Adds a list of keyframes to the collection.
       *
       * @param keyframes The list of keyframes to add.
       */
      fun addKeyframes(keyframes: List<Keyframe>) {
        keyframes.forEach(::addKeyframe)
      }

      /**
       * Clears all keyframes from the collection.
       */
      fun clearKeyframes() {
        keyframes as MutableList
        keyframes.clear()
      }

      /**
       * Retrieves a keyframe by its index.
       *
       * @param index The index of the keyframe to retrieve.
       * @return The keyframe at the specified index.
       */
      fun getKeyframe(index: Int): Keyframe {
        return keyframes[index]
      }

      /**
       * Removes a keyframe by its index.
       *
       * @param index The index of the keyframe to remove.
       */
      fun removeKeyframe(index: Int) {
        keyframes as MutableList
        keyframes.removeAt(index)
      }

      /**
       * Removes a keyframe from the collection.
       *
       * @param keyframe The keyframe to remove.
       */
      fun removeKeyframe(keyframe: Keyframe) {
        keyframes as MutableList
        keyframes.remove(keyframe)
      }

      override fun write(sink: Sink) {
        super.write(sink)
        properties.write(sink)
      }

    }


    @Serializable
    data object Null : Amf0Value(Amf0Type.NULL.byte) {

      override val size: Int = 1
    }

    @Serializable
    data class Number(val value: Double) : Amf0Value(Amf0Type.NUMBER.byte) {

      override val size: Int = 9

      override fun write(sink: Sink) {
        super.write(sink)
        sink.writeDouble(value)
      }

    }

    @Serializable
    data class Boolean(val value: kotlin.Boolean) : Amf0Value(Amf0Type.BOOLEAN.byte) {

      override val size: Int = 2

      override fun write(sink: Sink) {
        super.write(sink)
        sink.writeByte(if (value) 0x01 else 0x00)
      }
    }

    @Serializable
    data class String(val value: kotlin.String) : Amf0Value(Amf0Type.STRING.byte) {

      override val size: Int = 3 + value.toByteArray(Charsets.UTF_8).size

      override fun write(sink: Sink) {
        super.write(sink)
        val bytes = value.toByteArray(Charsets.UTF_8)
        sink.writeShort(bytes.size.toShort())
        sink.write(bytes)
      }
    }

    @Serializable
    data object Undefined : Amf0Value(Amf0Type.UNDEFINED.byte) {

      override val size: Int = 1
    }

    @Serializable
    data class Reference(val value: kotlin.UShort) : Amf0Value(Amf0Type.REFERENCE.byte) {

      override val size: Int = 3

      override fun write(sink: Sink) {
        super.write(sink)
        sink.writeUShort(value)
      }
    }

    @Serializable
    open class Object(open val properties: Map<kotlin.String, Amf0Value>) : Amf0Value(Amf0Type.OBJECT.byte) {

      override val size: Int
        get() {
          var totalSize = 1  // type byte
          properties.forEach { (key, value) ->
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            totalSize += 2 + keyBytes.size + value.size // key length (2) + key + value
          }
          totalSize += 3  // end marker (2 bytes empty string + 1 byte object end)
          return totalSize
        }

      override fun write(sink: Sink) {
        super.write(sink)
        properties.write(sink)
      }

      override fun toString(): kotlin.String {
        return "Object(${properties.entries.joinToString(", ") { (key, value) -> "$key: $value" }})"
      }

      override fun equals(other: Any?): kotlin.Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as Object

        return properties == other.properties
      }

      override fun hashCode(): Int {
        return properties.hashCode()
      }

    }

    @Serializable
    data class EcmaArray(val properties: Map<kotlin.String, Amf0Value>) : Amf0Value(Amf0Type.ECMA_ARRAY.byte) {

      override val size: Int
        get() {
          var totalSize = 1 + 4  // type byte + array length
          properties.forEach { (key, value) ->
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            totalSize += 2 + keyBytes.size + value.size
          }
          totalSize += 3  // end marker
          return totalSize
        }


      override fun write(sink: Sink) {
        super.write(sink)
        sink.writeInt(properties.size)
        properties.write(sink)
      }
    }

    @Serializable
    data class StrictArray(val values: List<Amf0Value>) : Amf0Value(Amf0Type.STRICT_ARRAY.byte) {


      override val size: Int
        get() = 1 + 4 + values.sumOf { it.size }  // type + array length + values

      override fun write(sink: Sink) {
        super.write(sink)
        sink.writeInt(values.size)
        values.forEach { it.write(sink) }
      }
    }

    @Serializable
    data class Date(val value: Double, val timezone: Short) : Amf0Value(Amf0Type.DATE.byte) {

      override val size: Int = 11

      override fun write(sink: Sink) {
        super.write(sink)
        sink.writeDouble(value)
        sink.writeShort(timezone)
      }
    }

    @Serializable
    data class LongString(val value: kotlin.String) : Amf0Value(Amf0Type.LONG_STRING.byte) {

      override val size: Int
        get() = 1 + 4 + value.toByteArray(Charsets.UTF_8).size  // type + length + string

      override fun write(sink: Sink) {
        super.write(sink)
        val bytes = value.toByteArray(Charsets.UTF_8)
        sink.writeInt(bytes.size)
        sink.write(bytes)
      }
    }

    @Serializable
    data class XmlDocument(val value: kotlin.String) : Amf0Value(Amf0Type.XML_DOCUMENT.byte) {

      override val size: Int
        get() = 1 + 4 + value.toByteArray(Charsets.UTF_8).size  // type + length + xml

      override fun write(sink: Sink) {
        super.write(sink)
        val bytes = value.toByteArray(Charsets.UTF_8)
        sink.writeInt(bytes.size)
        sink.write(bytes)
      }

    }

    @Serializable
    data class TypedObject(val className: kotlin.String, val properties: Map<kotlin.String, Amf0Value>) :
      Amf0Value(Amf0Type.TYPED_OBJECT.byte) {

      override val size: Int
        get() {
          var totalSize = 1  // type byte
          val classNameBytes = className.toByteArray(Charsets.UTF_8)
          totalSize += 2 + classNameBytes.size  // class name length + class name
          properties.forEach { (key, value) ->
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            totalSize += 2 + keyBytes.size + value.size
          }
          totalSize += 3  // end marker
          return totalSize
        }

      override fun write(sink: Sink) {
        super.write(sink)
        val bytes = className.toByteArray(Charsets.UTF_8)
        sink.writeShort(bytes.size.toShort())
        sink.write(bytes)
        properties.write(sink)
      }
    }

    protected fun Map<kotlin.String, Amf0Value>.write(sink: Sink) {
      this.forEach { (key, value) ->
        val bytes = key.toByteArray(Charsets.UTF_8)
        sink.writeShort(bytes.size.toShort())
        sink.write(bytes)
        value.write(sink)
      }
      sink.writeShort(0) // Empty string key to mark end
      sink.writeByte(Amf0Type.OBJECT_END.byte) // End marker
    }
  }

}


