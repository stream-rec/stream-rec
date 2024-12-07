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

import github.hua0512.flv.utils.writeU29
import github.hua0512.flv.utils.writeUtf8
import kotlinx.io.Sink
import kotlinx.io.writeDouble
import kotlinx.serialization.Serializable


/**
 * AMF3 data types
 * @author hua0512
 * @date : 2024/6/9 9:58
 * @see <a href="https://en.wikipedia.org/wiki/Action_Message_Format#AMF3">AMF3 spec</a>
 */
enum class Amf3Type(val byte: Byte) {
  UNDEFINED(0x00),
  NULL(0x01),
  BOOLEAN_FALSE(0x02),
  BOOLEAN_TRUE(0x03),
  INTEGER(0x04),
  DOUBLE(0x05),
  STRING(0x06),
  XML_DOCUMENT(0x07),
  DATE(0x08),
  ARRAY(0x09),
  OBJECT(0x0A),
  XML(0x0B),
  BYTEARRAY(0x0C)
}

/**
 * AMF3 value class
 * @author hua0512
 * @date : 2024/6/8 20:24
 */
@Serializable
sealed class Amf3Value(val type: Amf3Type) : AmfValue {

  override fun write(sink: Sink) {
    sink.writeByte(type.byte)
  }
}

@Serializable
data object Amf3Undefined : Amf3Value(Amf3Type.UNDEFINED) {

  override val size: Int = 1

  override fun write(sink: Sink) {
    super.write(sink)
  }
}

@Serializable
data object Amf3Null : Amf3Value(Amf3Type.NULL) {

  override val size: Int = 1

  override fun write(sink: Sink) {
    super.write(sink)
  }
}

@Serializable
data object Amf3BooleanFalse : Amf3Value(Amf3Type.BOOLEAN_FALSE) {

  override val size: Int = 1

  override fun write(sink: Sink) {
    super.write(sink)
  }
}

@Serializable
data object Amf3BooleanTrue : Amf3Value(Amf3Type.BOOLEAN_TRUE) {

  override val size: Int = 1

  override fun write(sink: Sink) {
    super.write(sink)
  }

}

@Serializable
data class Amf3Integer(val value: Int) : Amf3Value(Amf3Type.INTEGER) {

  override val size: Int = 1 + when (value) {
    in -0x80..0x7F -> 0
    in -0x4000..0x3FFF -> 1
    in -0x200000..0x1FFFFF -> 2
    else -> 3
  }

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeU29(value)
  }
}

@Serializable
data class Amf3Double(val value: Double) : Amf3Value(Amf3Type.DOUBLE) {

  override val size: Int = 9

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeDouble(value)
  }
}

@Serializable
data class Amf3String(val value: String) : Amf3Value(Amf3Type.STRING) {

  override val size: Int = 1 + value.toByteArray(Charsets.UTF_8).size

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeUtf8(value)
  }
}

@Serializable
data class Amf3XmlDocument(val value: String) : Amf3Value(Amf3Type.XML_DOCUMENT) {

  override val size: Int = 1 + value.toByteArray(Charsets.UTF_8).size

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeUtf8(value)
  }
}

@Serializable
data class Amf3Date(val value: Double) : Amf3Value(Amf3Type.DATE) {

  override val size: Int = 9

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeByte(0) // No timezone
    sink.writeDouble(value)
  }
}

@Serializable
data class Amf3Array(val values: List<Amf3Value>) : Amf3Value(Amf3Type.ARRAY) {

  override val size: Int = 1 + 4 + values.sumOf { it.size }

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeU29(values.size shl 1 or 1)
    sink.writeByte(0x01) // Empty string key to mark end of associative part
    values.forEach { it.write(sink) }
  }
}

@Serializable
data class Amf3Object(val traits: List<String>, val properties: Map<String, Amf3Value>) : Amf3Value(Amf3Type.OBJECT) {

  override val size: Int
    get() {
      var size = 1
      size += 4 // Traits
      traits.forEach { size += 2 + it.toByteArray(Charsets.UTF_8).size }
      properties.forEach { (key, value) ->
        size += 2 + key.toByteArray(Charsets.UTF_8).size
        size += value.size
      }
      size += 1 // Empty string key to mark end of associative part
      return size
    }

  override fun write(sink: Sink) {
    super.write(sink)
    val traitsInfo = (traits.size shl 4) or 0x03 // Traits, dynamic, externalizable
    sink.writeU29(traitsInfo)
    sink.writeUtf8("") // Empty class name for dynamic class
    traits.forEach { sink.writeUtf8(it) }
    properties.forEach { (key, value) ->
      sink.writeUtf8(key)
      value.write(sink)
    }
    sink.writeByte(0x01) // Empty string key to mark end of associative part
  }
}

@Serializable
data class Amf3Xml(val value: String) : Amf3Value(Amf3Type.XML) {

  override val size: Int = 1 + value.toByteArray(Charsets.UTF_8).size

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeUtf8(value)
  }
}

@Serializable
data class Amf3ByteArray(val value: ByteArray) : Amf3Value(Amf3Type.BYTEARRAY) {

  override val size: Int = 1 + 4 + value.size

  override fun write(sink: Sink) {
    super.write(sink)
    sink.writeU29(value.size shl 1 or 1)
    sink.write(value)
  }
}

@Serializable
data class Amf3Reference(val index: Int) : Amf3Value(Amf3Type.OBJECT) {

  override val size: Int = 1

  override fun write(sink: Sink) {
    throw UnsupportedOperationException("References are not supported in write")
  }
}