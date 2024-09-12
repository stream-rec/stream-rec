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
import java.util.LinkedHashMap

/**
 * Read AMF0 value from input stream
 * @author hua0512
 * @date : 2024/6/9 20:13
 */
fun readAmf0Value(input: DataInputStream): Amf0Value {
  val type = input.readByte()

  return when (type) {
    Amf0Type.NUMBER.byte -> Amf0Value.Number(input.readDouble())
    Amf0Type.BOOLEAN.byte -> Amf0Value.Boolean(input.readBoolean())
    Amf0Type.STRING.byte, Amf0Type.LONG_STRING.byte, Amf0Type.XML_DOCUMENT.byte -> {
      val length: Int = if (type == Amf0Type.STRING.byte) input.readUnsignedShort() else input.readInt()
      val str = ByteArray(length)
      input.readFully(str)
      when (type) {
        Amf0Type.STRING.byte -> Amf0Value.String(String(str).trim(0.toChar()))
        Amf0Type.LONG_STRING.byte -> Amf0Value.LongString(String(str).trim(0.toChar()))
        else -> Amf0Value.XmlDocument(String(str).trim(0.toChar()))
      }
    }

    Amf0Type.OBJECT.byte -> readAmf0Object(input)
    Amf0Type.ECMA_ARRAY.byte -> readAmf0EcmaArray(input)
    Amf0Type.STRICT_ARRAY.byte -> readAmf0StrictArray(input)
    Amf0Type.DATE.byte -> readAmf0Date(input)
    Amf0Type.AMF3_OBJECT.byte -> readAmf0TypedObject(input)
    Amf0Type.NULL.byte -> Amf0Value.Null
    Amf0Type.TYPED_OBJECT.byte -> readAmf0TypedObject(input)
    else -> throw IllegalArgumentException("Unsupported AMF0 type: $type")
  }
}


fun readAmf0Object(input: DataInputStream): Amf0Value.Object {
  val properties = LinkedHashMap<kotlin.String, Amf0Value>()

  while (true) {
    val keyLength = input.readUnsignedShort()
    if (keyLength == 0) {
      // End of object (empty key with marker)
      input.readByte() // Read and discard the marker byte
      break
    }

    val keyBytes = ByteArray(keyLength)
    input.readFully(keyBytes)
    val key = String(keyBytes)

    val value = readAmf0Value(input)
    properties[key] = value
  }

  return Amf0Value.Object(properties)
}

fun readAmf0EcmaArray(input: DataInputStream): Amf0Value.EcmaArray {
  val arrayLength = input.readInt()
  val properties = mutableMapOf<kotlin.String, Amf0Value>()

  while (true) {
    val keyLength = input.readUnsignedShort()
    if (keyLength == 0) {
      input.readByte() // Read and discard the marker byte
      break
    }

    val keyBytes = ByteArray(keyLength)
    input.readFully(keyBytes)
    val key = String(keyBytes)

    val value = readAmf0Value(input)
    properties[key] = value
  }

  return Amf0Value.EcmaArray(properties)
}

fun readAmf0StrictArray(input: DataInputStream): Amf0Value.StrictArray {
  val arrayLength = input.readInt()
  val values = mutableListOf<Amf0Value>()

  repeat(arrayLength) {
    values.add(readAmf0Value(input))
  }

  return Amf0Value.StrictArray(values)
}

fun readAmf0Date(input: DataInputStream): Amf0Value.Date {
  val timestamp = input.readDouble()
  val timezone = input.readShort()
  return Amf0Value.Date(timestamp, timezone)
}

fun readAmf0TypedObject(input: DataInputStream): Amf0Value.TypedObject {
  val classNameLength = input.readUnsignedShort()
  val classNameBytes = ByteArray(classNameLength)
  input.readFully(classNameBytes)
  val className = String(classNameBytes)

  val properties = mutableMapOf<kotlin.String, Amf0Value>()

  while (true) {
    val keyLength = input.readUnsignedShort()
    if (keyLength == 0) {
      input.readByte() // Read and discard the marker byte
      break
    }

    val keyBytes = ByteArray(keyLength)
    input.readFully(keyBytes)
    val key = String(keyBytes)

    val value = readAmf0Value(input)
    properties[key] = value
  }

  return Amf0Value.TypedObject(className, properties)
}