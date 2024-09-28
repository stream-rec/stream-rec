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

import java.io.DataInputStream

/**
 * Read AMF3 value from input stream
 * @author hua0512
 * @date : 2024/6/8 20:47
 */
fun readAmf3Value(input: DataInputStream): Amf3Value {
  val type = input.readByte()
  return when (type) {
    Amf3Type.UNDEFINED.byte -> Amf3Undefined
    Amf3Type.NULL.byte -> Amf3Null
    Amf3Type.BOOLEAN_FALSE.byte -> Amf3BooleanFalse
    Amf3Type.BOOLEAN_TRUE.byte -> Amf3BooleanTrue
    Amf3Type.INTEGER.byte -> Amf3Integer(readU29(input))
    Amf3Type.DOUBLE.byte -> Amf3Double(input.readDouble())
    Amf3Type.STRING.byte -> readAmf3String(input)
    Amf3Type.XML_DOCUMENT.byte -> Amf3XmlDocument(readUtf8(input))
    Amf3Type.DATE.byte -> readAmf3Date(input)
    Amf3Type.ARRAY.byte -> readAmf3Array(input)
    Amf3Type.OBJECT.byte -> readAmf3Object(input)
    Amf3Type.XML.byte -> Amf3Xml(readUtf8(input))
    Amf3Type.BYTEARRAY.byte -> {
      val length = readU29(input) shr 1
      val byteArray = ByteArray(length)
      input.read(byteArray)
      return Amf3ByteArray(byteArray)
    }

    else -> throw IllegalArgumentException("Unknown AMF3 type: $type")
  }
}

private fun readAmf3String(input: DataInputStream): Amf3String {
  return Amf3String(readUtf8(input))
}

private fun readAmf3Date(input: DataInputStream): Amf3Date {
  input.readByte() // Ignore timezone
  val timestamp = input.readDouble()
  return Amf3Date(timestamp)
}

private fun readAmf3Array(input: DataInputStream): Amf3Array {
  val u29 = readU29(input)
  if (u29.and(1) == 0) {
    val length = u29 shr 1
    val array = mutableListOf<Amf3Value>()
    repeat(length) {
      array.add(readAmf3Value(input))
    }
    return Amf3Array(array)
  } else {
    val length = u29 shr 1
    val array = mutableListOf<Amf3Value>()
    val key = readUtf8(input)
    while (key.isNotEmpty()) {
      array.add(Amf3String(key))
      array.add(readAmf3Value(input))
    }
    return Amf3Array(array)
  }
}


private fun readAmf3Object(input: DataInputStream): Amf3Value {
  val u29 = readU29(input)
  if (u29.and(1) == 0) {
    return Amf3Reference(u29.ushr(1))
  }

  val className = readAmf3String(input)
  if (className.value.isEmpty()) {
    return Amf3Reference(u29.ushr(1))
  }

  val flags = u29.ushr(1)
  if (flags.and(1) == 0) {
    return Amf3Object(emptyList(), emptyMap())
  }

  if (flags.and(4) == 4) {
    val externalizable = input.readBoolean()
    if (externalizable) {
      throw UnsupportedOperationException("Externalizable objects are not supported.")
    }
  }

  val dynamic = flags.and(8) == 8
  val numTraits = u29.ushr(4)
  val traits: MutableList<Amf3String> = mutableListOf()
  for (i in 0 until numTraits) {
    traits.add(readAmf3String(input))
  }

  val properties: MutableMap<Amf3String, Amf3Value> = mutableMapOf()

  if (dynamic) {
    while (true) {
      val key = readAmf3String(input)
      if (key.value.isEmpty()) {
        break
      }
      val value = readAmf3Object(input)
      properties[key] = value
    }
  }
  return Amf3Object(traits.map { it.value }, properties.mapKeys { it.key.value })
}


private fun readU29(input: DataInputStream): Int {
  var result = 0
  var shift = 0
  var b: Int
  do {
    b = input.readUnsignedByte().toInt()
    result = result or ((b and 0x7f) shl shift)
    shift += 7
  } while (b and 0x80 != 0)
  return result
}


private fun readUtf8(input: DataInputStream): String {
  val u29 = readU29(input)
  if (u29.and(1) == 0) {
    return ""
  }
  val length = u29 shr 1
  val bytes = ByteArray(length)
  input.readFully(bytes)
  return String(bytes, Charsets.UTF_8)
}
