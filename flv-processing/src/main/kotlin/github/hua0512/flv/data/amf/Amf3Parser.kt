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

import io.ktor.utils.io.readText
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readDouble
import kotlinx.io.readString
import kotlinx.io.readUByte

/**
 * Read AMF3 value from input stream
 * @author hua0512
 * @date : 2024/6/8 20:47
 */
fun readAmf3Value(source: Source): Amf3Value {
  val type = source.readByte()
  return when (type) {
    Amf3Type.UNDEFINED.byte -> Amf3Undefined
    Amf3Type.NULL.byte -> Amf3Null
    Amf3Type.BOOLEAN_FALSE.byte -> Amf3BooleanFalse
    Amf3Type.BOOLEAN_TRUE.byte -> Amf3BooleanTrue
    Amf3Type.INTEGER.byte -> Amf3Integer(readU29(source))
    Amf3Type.DOUBLE.byte -> Amf3Double(source.readDouble())
    Amf3Type.STRING.byte -> readAmf3String(source)
    Amf3Type.XML_DOCUMENT.byte -> Amf3XmlDocument(readUtf8(source))
    Amf3Type.DATE.byte -> readAmf3Date(source)
    Amf3Type.ARRAY.byte -> readAmf3Array(source)
    Amf3Type.OBJECT.byte -> readAmf3Object(source)
    Amf3Type.XML.byte -> Amf3Xml(readUtf8(source))
    Amf3Type.BYTEARRAY.byte -> {
      val length = readU29(source) shr 1
      val byteArray = source.readByteArray(length)
      return Amf3ByteArray(byteArray)
    }

    else -> throw IllegalArgumentException("Unknown AMF3 type: $type")
  }
}

private fun readAmf3String(source: Source): Amf3String = Amf3String(source.readText())

private fun readAmf3Date(source: Source): Amf3Date {
  source.readByte() // Ignore timezone
  val timestamp = source.readDouble()
  return Amf3Date(timestamp)
}

private fun readAmf3Array(source: Source): Amf3Array {
  val u29 = readU29(source)
  if (u29.and(1) == 0) {
    val length = u29 shr 1
    val array = mutableListOf<Amf3Value>()
    repeat(length) {
      array.add(readAmf3Value(source))
    }
    return Amf3Array(array)
  } else {
    val length = u29 shr 1
    val array = mutableListOf<Amf3Value>()
    val key = readUtf8(source)
    while (key.isNotEmpty()) {
      array.add(Amf3String(key))
      array.add(readAmf3Value(source))
    }
    return Amf3Array(array)
  }
}


private fun readAmf3Object(source: Source): Amf3Value {
  val u29 = readU29(source)
  if (u29.and(1) == 0) {
    return Amf3Reference(u29.ushr(1))
  }

  val className = readAmf3String(source)
  if (className.value.isEmpty()) {
    return Amf3Reference(u29.ushr(1))
  }

  val flags = u29.ushr(1)
  if (flags.and(1) == 0) {
    return Amf3Object(emptyList(), emptyMap())
  }

  if (flags.and(4) == 4) {
    val externalizable = source.readByte() == 1.toByte()
    if (externalizable) {
      throw UnsupportedOperationException("Externalizable objects are not supported.")
    }
  }

  val dynamic = flags.and(8) == 8
  val numTraits = u29.ushr(4)
  val traits: MutableList<Amf3String> = mutableListOf()
  for (i in 0 until numTraits) {
    traits.add(readAmf3String(source))
  }

  val properties: MutableMap<Amf3String, Amf3Value> = mutableMapOf()

  if (dynamic) {
    while (true) {
      val key = readAmf3String(source)
      if (key.value.isEmpty()) {
        break
      }
      val value = readAmf3Object(source)
      properties[key] = value
    }
  }
  return Amf3Object(traits.map { it.value }, properties.mapKeys { it.key.value })
}


private fun readU29(source: Source): Int {
  var result = 0
  var shift = 0
  var b: Int
  do {
    b = source.readUByte().toInt()
    result = result or ((b and 0x7f) shl shift)
    shift += 7
  } while (b and 0x80 != 0)
  return result
}


private fun readUtf8(source: Source): String {
  val u29 = readU29(source)
  if (u29.and(1) == 0) {
    return ""
  }
  val length = u29 shr 1

  return source.readString(length.toLong())
}
