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
 * AMF3 value class
 * @author hua0512
 * @date : 2024/6/8 20:24
 */
sealed class Amf3Value(val type: Amf3Type) : AmfValue {

  // TODO : implement size
  override val size: Int = 0

  override fun write(output: OutputStream) {
    output.write(type.byte.toInt())
  }
}

data object Amf3Undefined : Amf3Value(Amf3Type.UNDEFINED) {

  override fun write(output: OutputStream) {
    super.write(output)
  }
}

data object Amf3Null : Amf3Value(Amf3Type.NULL) {

  override fun write(output: OutputStream) {
    super.write(output)
  }
}

data object Amf3BooleanFalse : Amf3Value(Amf3Type.BOOLEAN_FALSE) {

  override fun write(output: OutputStream) {
    super.write(output)
  }
}

data object Amf3BooleanTrue : Amf3Value(Amf3Type.BOOLEAN_TRUE) {
  override fun write(output: OutputStream) {
    super.write(output)
  }

}

data class Amf3Integer(val value: Int) : Amf3Value(Amf3Type.INTEGER) {

  override fun write(output: OutputStream) {
    super.write(output)
    output.writeU29(value)
  }
}

data class Amf3Double(val value: Double) : Amf3Value(Amf3Type.DOUBLE) {

  override fun write(output: OutputStream) {
    super.write(output)
    output.writeDouble(value)
  }
}

data class Amf3String(val value: String) : Amf3Value(Amf3Type.STRING) {

  override fun write(output: OutputStream) {
    super.write(output)
    output.writeUtf8(value)
  }
}

data class Amf3XmlDocument(val value: String) : Amf3Value(Amf3Type.XML_DOCUMENT) {

  override fun write(output: OutputStream) {
    super.write(output)
    output.writeUtf8(value)
  }
}

data class Amf3Date(val value: Double) : Amf3Value(Amf3Type.DATE) {

  override fun write(output: OutputStream) {
    super.write(output)
    output.write(0) // No timezone
    output.writeDouble(value)
  }
}

data class Amf3Array(val values: List<Amf3Value>) : Amf3Value(Amf3Type.ARRAY) {

  override fun write(output: OutputStream) {
    super.write(output)
    output.writeU29(values.size shl 1 or 1)
    output.write(0x01) // Empty string key to mark end of associative part
    values.forEach { it.write(output) }
  }
}

data class Amf3Object(val traits: List<String>, val properties: Map<String, Amf3Value>) : Amf3Value(Amf3Type.OBJECT) {
  override fun write(output: OutputStream) {
    super.write(output)
    val traitsInfo = (traits.size shl 4) or 0x03 // Traits, dynamic, externalizable
    output.writeU29(traitsInfo)
    output.writeUtf8("") // Empty class name for dynamic class
    traits.forEach { output.writeUtf8(it) }
    properties.forEach { (key, value) ->
      output.writeUtf8(key)
      value.write(output)
    }
    output.write(0x01) // Empty string key to mark end of associative part
  }
}

data class Amf3Xml(val value: String) : Amf3Value(Amf3Type.XML) {
  override fun write(output: OutputStream) {
    super.write(output)
    output.writeUtf8(value)
  }
}

data class Amf3ByteArray(val value: ByteArray) : Amf3Value(Amf3Type.BYTEARRAY) {
  override fun write(output: OutputStream) {
    super.write(output)
    output.writeU29(value.size shl 1 or 1)
    output.write(value)
  }
}


data class Amf3Reference(val index: Int) : Amf3Value(Amf3Type.OBJECT) {

  override fun write(output: OutputStream) {
    throw UnsupportedOperationException("References are not supported in write")
  }
}


internal fun OutputStream.writeU29(value: Int) {
  var v = value
  if (v < 0x80) {
    write(v)
  } else if (v < 0x4000) {
    write((v shr 7 and 0x7F or 0x80))
    write(v and 0x7F)
  } else if (v < 0x200000) {
    write((v shr 14 and 0x7F or 0x80))
    write((v shr 7 and 0x7F or 0x80))
    write(v and 0x7F)
  } else if (v < 0x40000000) {
    write((v shr 22 and 0x7F or 0x80))
    write((v shr 15 and 0x7F or 0x80))
    write((v shr 8 and 0x7F or 0x80))
    write(v and 0xFF)
  } else {
    write((v shr 29 and 0x7F or 0x80))
    write((v shr 22 and 0x7F or 0x80))
    write((v shr 15 and 0x7F or 0x80))
    write((v shr 8 and 0x7F or 0x80))
    write(v and 0xFF)
  }
}

internal fun OutputStream.writeUtf8(value: String) {
  val bytes = value.toByteArray(Charsets.UTF_8)
  writeU29(bytes.size shl 1 or 1)
  write(bytes)
}

