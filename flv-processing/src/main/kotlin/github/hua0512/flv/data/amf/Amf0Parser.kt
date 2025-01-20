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

import github.hua0512.flv.data.amf.AmfValue.Amf0Value
import kotlinx.io.Source
import kotlinx.io.readDouble
import kotlinx.io.readString
import kotlinx.io.readUShort

/**
 * Read AMF0 value from input stream
 * @author hua0512
 * @date : 2024/6/9 20:13
 */
fun readAmf0Value(source: Source): Amf0Value {
  return when (val type = source.readByte()) {
    Amf0Type.NUMBER.byte -> Amf0Value.Number(source.readDouble())

    Amf0Type.BOOLEAN.byte -> Amf0Value.Boolean(source.readByte() != 0.toByte())
    Amf0Type.STRING.byte, Amf0Type.LONG_STRING.byte, Amf0Type.XML_DOCUMENT.byte -> {
      val length: Int = if (type == Amf0Type.STRING.byte) source.readUShort().toInt() else source.readInt()
      val str = source.readString(length.toLong())
      when (type) {
        Amf0Type.STRING.byte -> Amf0Value.String(str.trim(0.toChar()))
        Amf0Type.LONG_STRING.byte -> Amf0Value.LongString(str.trim(0.toChar()))
        else -> Amf0Value.XmlDocument(str.trim(0.toChar()))
      }
    }

    Amf0Type.UNDEFINED.byte -> Amf0Value.Undefined
    Amf0Type.REFERENCE.byte -> Amf0Value.Reference(source.readUShort())

    Amf0Type.OBJECT.byte -> readAmf0Object(source)
    Amf0Type.ECMA_ARRAY.byte -> readAmf0EcmaArray(source)
    Amf0Type.STRICT_ARRAY.byte -> readAmf0StrictArray(source)
    Amf0Type.DATE.byte -> Amf0Value.Date(source.readDouble(), source.readShort())
    Amf0Type.AMF3_OBJECT.byte, Amf0Type.TYPED_OBJECT.byte -> readAmf0TypedObject(source)
    Amf0Type.NULL.byte -> Amf0Value.Null
    else -> throw IllegalArgumentException("Unsupported AMF0 type: $type")
  }
}

private fun readAmf0Object(source: Source): AmfValue.Amf0Value.Object {
  val properties = readAmf0Properties(source)
  return Amf0Value.Object(properties)
}

private fun readAmf0EcmaArray(source: Source): Amf0Value.EcmaArray {
  val arrayLength = source.readInt()
  require(arrayLength >= 0) { "Invalid array length: $arrayLength" }
  val properties = readAmf0Properties(source)
  return Amf0Value.EcmaArray(properties)
}

private fun readAmf0StrictArray(source: Source): Amf0Value.StrictArray {
  val arrayLength = source.readInt()
  val values = buildList<Amf0Value> {
    repeat(arrayLength) {
      add(readAmf0Value(source))
    }
  }
  return Amf0Value.StrictArray(values)
}

private fun readAmf0TypedObject(source: Source): Amf0Value.TypedObject {
  val classNameLength = source.readUShort().toLong()
  val classNameKey = source.readString(classNameLength)
  val properties = readAmf0Properties(source)
  return Amf0Value.TypedObject(classNameKey, properties)
}

private fun readAmf0Properties(source: Source): MutableMap<String, Amf0Value> {
  val properties = mutableMapOf<String, Amf0Value>()
  while (true) {
    val keyLength = source.readUShort().toLong()
    if (keyLength == 0L) {
      source.skip(1) // Read and discard the marker byte
      break
    }
    val key = source.readString(keyLength)
    val value = readAmf0Value(source)
    properties[key] = value
  }

  return properties
}