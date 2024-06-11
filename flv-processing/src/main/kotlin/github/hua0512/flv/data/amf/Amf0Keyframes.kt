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

import github.hua0512.flv.utils.Keyframe
import java.io.DataOutputStream

/**
 * AMF0 Keyframes object
 * @author hua0512
 * @date : 2024/9/9 20:43
 */
internal data class Amf0Keyframes(val keyframes: ArrayList<Keyframe> = ArrayList()) : Amf0Value(Amf0Type.OBJECT.byte) {

  companion object {
    private const val KEY_TIMES = "times"
    internal const val KEY_FILEPOSITIONS = "filepositions"
    internal const val KEY_SPACER = "spacer"

    internal const val MAX_KEYFRAMES_PERMITTED = 6300
    private const val MIN_INTERVAL_BETWEEN_KEYFRAMES = 1900
  }


  val properties: Map<kotlin.String, Amf0Value> = mapOf(
    KEY_TIMES to StrictArray(keyframes.map { Number(it.timestamp / 1000.0) }),
    KEY_FILEPOSITIONS to StrictArray(keyframes.map { Number(it.filePosition.toDouble()) }),
    KEY_SPACER to StrictArray(List((MAX_KEYFRAMES_PERMITTED - keyframes.size) * 2) { Number(Double.NaN) })
  )


  fun addKeyframe(keyframe: Keyframe) {
    if (keyframes.size >= MAX_KEYFRAMES_PERMITTED) {
      throw IllegalArgumentException("Keyframes size exceeds the maximum limit of $MAX_KEYFRAMES_PERMITTED")
    }

    if (keyframes.isEmpty() || (keyframe.timestamp - keyframes.last().timestamp) >= MIN_INTERVAL_BETWEEN_KEYFRAMES) {
      keyframes.add(keyframe)
    }
  }

  fun addKeyframes(keyframes: List<Keyframe>) {
    keyframes.forEach(::addKeyframe)
  }

  fun clearKeyframes() {
    keyframes.clear()
  }

  fun getKeyframes(): List<Keyframe> {
    return keyframes
  }

  fun getKeyframe(index: Int): Keyframe {
    return keyframes[index]
  }

  fun removeKeyframe(index: Int) {
    keyframes.removeAt(index)
  }

  fun removeKeyframe(keyframe: Keyframe) {
    keyframes.remove(keyframe)
  }

  fun size(): Int {
    return keyframes.size
  }


  override fun write(output: DataOutputStream) {
    output.writeByte(Amf0Type.OBJECT.byte.toInt())
    properties.forEach { (key, value) ->
      val bytes = key.toByteArray(Charsets.UTF_8)
      output.writeShort(bytes.size)
      output.write(bytes)
      value.write(output)
    }
    output.writeShort(0) // Empty string key to mark end
    output.writeByte(0x09) // End marker
  }


}
