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

package github.hua0512.flv

import github.hua0512.flv.data.FlvData
import org.slf4j.LoggerFactory

/**
 * A provider class for managing FLV tags in memory.
 * This class allows adding, updating, retrieving, and removing FLV tags using their CRC32 checksums.
 * It also provides a method to clear all stored tags.
 *
 * @constructor Creates an empty FlvMemoryProvider.
 */
internal class FlvMemoryProvider {

  private companion object {
    private const val TAG = "FlvMemoryProvider"
    private val logger = LoggerFactory.getLogger(TAG)
  }

  // A map to store FLV tags using their CRC32 checksums as keys.
  private val tagMap = mutableMapOf<Long, FlvData>()

  /**
   * Adds a new FLV tag to the provider.
   * If a tag with the same CRC32 checksum already exists, a warning is logged and the tag is not added.
   *
   * @param tag The FLV tag to add.
   */
  fun put(tag: FlvData) {
    if (tagMap.containsKey(tag.crc32)) {
      logger.warn("tag already exists, crc32: ${tag.crc32}")
      return
    }
    tagMap[tag.crc32] = tag
  }

  /**
   * Updates an existing FLV tag in the provider.
   * The old tag is removed and replaced with the new tag.
   *
   * @param oldCrc32 The CRC32 checksum of the old tag to be replaced.
   * @param newTag The new FLV tag to add.
   */
  fun update(oldCrc32: Long, newTag: FlvData) {
    tagMap.remove(oldCrc32)
    tagMap[newTag.crc32] = newTag
  }

  /**
   * Retrieves an FLV tag from the provider using its CRC32 checksum.
   * If the tag is not found, a warning is logged and null is returned.
   *
   * @param crc32 The CRC32 checksum of the tag to retrieve.
   * @return The FLV tag if found, or null if not found.
   */
  fun get(crc32: Long): FlvData? {
    return tagMap[crc32] ?: run {
      logger.warn("tag not found, crc32: $crc32")
      null
    }
  }

  /**
   * Removes an FLV tag from the provider using its CRC32 checksum.
   *
   * @param crc32 The CRC32 checksum of the tag to remove.
   */
  fun remove(crc32: Long) {
    tagMap.remove(crc32)
  }

  /**
   * Clears all FLV tags from the provider.
   */
  fun clear() {
    tagMap.clear()
  }

}