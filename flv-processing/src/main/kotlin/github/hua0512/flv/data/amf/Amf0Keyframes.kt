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
internal data class Amf0Keyframes(internal val keyframes: ArrayList<Keyframe> = ArrayList()) : Amf0Value.Object(emptyMap()) {

  companion object {
    private const val KEY_TIMES = "times"
    internal const val KEY_FILEPOSITIONS = "filepositions"
    internal const val KEY_SPACER = "spacer"

    internal const val MAX_KEYFRAMES_PERMITTED = 6300
    private const val MIN_INTERVAL_BETWEEN_KEYFRAMES = 1900
  }

  /**
   * The properties of the AMF0 Keyframes object.
   *
   * @return A map containing the keyframe times, file positions, and spacer values.
   */
  override val properties: Map<kotlin.String, Amf0Value>
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
    keyframes.clear()
  }

  /**
   * Retrieves the list of keyframes.
   *
   * @return The list of keyframes.
   */
  fun getKeyframes(): List<Keyframe> {
    return keyframes
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
    keyframes.removeAt(index)
  }

  /**
   * Removes a keyframe from the collection.
   *
   * @param keyframe The keyframe to remove.
   */
  fun removeKeyframe(keyframe: Keyframe) {
    keyframes.remove(keyframe)
  }

}