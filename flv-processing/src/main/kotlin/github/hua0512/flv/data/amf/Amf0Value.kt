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

package github.hua0512.flv.data.amf

import java.io.OutputStream

/**
 * AMF0 data values
 * @author hua0512
 * @date : 2024/6/8 20:24
 */
sealed class Amf0Value(open val type: Byte) : AmfValue {

  override fun write(output: OutputStream) {
    output.write(type.toInt())
  }


  data object Null : Amf0Value(Amf0Type.NULL.byte) {

    override val size: Int = 1
  }


  data class Number(val value: Double) : Amf0Value(Amf0Type.NUMBER.byte) {

    override val size: Int = 9

    override fun write(output: OutputStream) {
      super.write(output)
      output.writeDouble(value)
    }

  }

  data class Boolean(val value: kotlin.Boolean) : Amf0Value(Amf0Type.BOOLEAN.byte) {

    override val size: Int = 2

    override fun write(output: OutputStream) {
      super.write(output)
      output.write(if (value) 0x01 else 0x00)
    }
  }

  data class String(val value: kotlin.String) : Amf0Value(Amf0Type.STRING.byte) {

    override val size: Int = 3 + value.toByteArray(Charsets.UTF_8).size

    override fun write(output: OutputStream) {
      super.write(output)
      val bytes = value.toByteArray(Charsets.UTF_8)
      output.writeShort(bytes.size)
      output.write(bytes)
    }
  }

  data class Object(val properties: Map<kotlin.String, Amf0Value>) : Amf0Value(Amf0Type.OBJECT.byte) {

    override val size: Int
      get() {
        // 1 byte type + 4 byte type marker
        var totalSize = 5
        properties.forEach { (key, value) ->
          val keyBytes = key.toByteArray(Charsets.UTF_8)
          totalSize += 2 + keyBytes.size + value.size
        }
        // + 3 byte end marker
        totalSize += 3
        return totalSize
      }

    override fun write(output: OutputStream) {
      super.write(output)
      properties.write(output)
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

  data class EcmaArray(val properties: Map<kotlin.String, Amf0Value>) : Amf0Value(Amf0Type.ECMA_ARRAY.byte) {

    override val size: Int
      get() {
        // 1 byte type + 4 byte type marker
        var totalSize = 5
        properties.forEach { (key, value) ->
          val keyBytes = key.toByteArray(Charsets.UTF_8)
          totalSize += 2 + keyBytes.size + value.size
        }
        // + 3 byte end marker
        totalSize += 3
        return totalSize
      }


    override fun write(output: OutputStream) {
      super.write(output)
      output.writeInt(properties.size)
      properties.write(output)
    }
  }

  data class StrictArray(val values: List<Amf0Value>) : Amf0Value(Amf0Type.STRICT_ARRAY.byte) {


    override val size: Int = 5 + values.sumOf { it.size }

    override fun write(output: OutputStream) {
      super.write(output)
      output.writeInt(values.size)
      values.forEach { it.write(output) }
    }
  }

  data class Date(val value: Double, val timezone: Short) : Amf0Value(Amf0Type.DATE.byte) {

    override val size: Int = 11

    override fun write(output: OutputStream) {
      super.write(output)
      output.writeDouble(value)
      output.writeShort(timezone.toInt())
    }
  }

  data class LongString(val value: kotlin.String) : Amf0Value(Amf0Type.LONG_STRING.byte) {

    // 1 byte type + 4 byte length + string bytes
    override val size: Int = 5 + value.toByteArray(Charsets.UTF_8).size

    override fun write(output: OutputStream) {
      super.write(output)
      val bytes = value.toByteArray(Charsets.UTF_8)
      output.writeInt(bytes.size)
      output.write(bytes)
    }
  }

  data class XmlDocument(val value: kotlin.String) : Amf0Value(Amf0Type.XML_DOCUMENT.byte) {

    override val size: Int = 5 + value.toByteArray(Charsets.UTF_8).size

    override fun write(output: OutputStream) {
      super.write(output)
      val bytes = value.toByteArray(Charsets.UTF_8)
      output.writeInt(bytes.size)
      output.write(bytes)
    }

  }

  data class TypedObject(val className: kotlin.String, val properties: Map<kotlin.String, Amf0Value>) : Amf0Value(Amf0Type.TYPED_OBJECT.byte) {

    override val size: Int
      get() {
        val classNameBytes = className.toByteArray(Charsets.UTF_8)
        var totalSize = 1 + 2 + classNameBytes.size
        properties.forEach { (key, value) ->
          val keyBytes = key.toByteArray(Charsets.UTF_8)
          totalSize += 2 + keyBytes.size + value.size
        }
        // + 3 byte end marker
        totalSize += 3
        return totalSize
      }

    override fun write(output: OutputStream) {
      super.write(output)
      val bytes = className.toByteArray(Charsets.UTF_8)
      output.writeShort(bytes.size)
      output.write(bytes)
      properties.write(output)
    }
  }

}

internal fun Map<kotlin.String, Amf0Value>.write(output: OutputStream) {
  this.forEach { (key, value) ->
    val bytes = key.toByteArray(Charsets.UTF_8)
    output.writeShort(bytes.size)
    output.write(bytes)
    value.write(output)
  }
  output.writeShort(0) // Empty string key to mark end
  output.write(0x09) // End marker
}


internal fun OutputStream.writeInt(value: Int) {
  // Write the integer in big-endian order (most significant byte first)
  write(value ushr 24)
  write(value ushr 16)
  write(value ushr 8)
  write(value)
}

internal fun OutputStream.writeShort(value: Int) {
  // Write the short in big-endian order (most significant byte first)
  write(value ushr 8)
  write(value)
}

internal fun OutputStream.writeDouble(value: Double) {
  // Write the double in big-endian order (most significant byte first)
  val longBits = java.lang.Double.doubleToLongBits(value)
  write((longBits ushr 56).toInt())
  write((longBits ushr 48).toInt())
  write((longBits ushr 40).toInt())
  write((longBits ushr 32).toInt())
  write((longBits ushr 24).toInt())
  write((longBits ushr 16).toInt())
  write((longBits ushr 8).toInt())
  write(longBits.toInt())
}