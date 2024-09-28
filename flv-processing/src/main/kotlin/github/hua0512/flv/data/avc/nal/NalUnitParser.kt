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

package github.hua0512.flv.data.avc.nal


private typealias ByteRandomAccess<T> = (container: T, index: Int) -> Byte

/**
 * A callback for Nal units in a range
 */
private typealias NalAccessor = (startCode: NalStartCode, start: Int, size: Int) -> Unit

private typealias onNalUnit = (NalUnit) -> Unit


/**
 * Nal unit parser
 * @author hua0512
 * @date : 2024/6/10 12:46
 */
internal object NalUnitParser {

  fun parseFromH264(data: ByteArray): List<NalUnit> {
    val nalUnits = mutableListOf<NalUnit>()
    var offset = 0
    while (offset < data.size) {
      offset = if (isAnnexBFormat(data, offset)) {
        parseAnnexBFormat(data, offset) { nalUnits.add(it) }
      } else {
        parseAvccFormat(data, offset) { nalUnits.add(it) }
      }
    }
    return nalUnits
  }

  private fun isAnnexBFormat(data: ByteArray, offset: Int): Boolean {
    // compare directly to avoid array copy
    return (data.size - offset >= 3 && data[offset] == 0x00.toByte() && data[offset + 1] == 0x00.toByte() && data[offset + 2] == 0x01.toByte()) ||
            (data.size - offset >= 4 && data[offset] == 0x00.toByte() && data[offset + 1] == 0x00.toByte() && data[offset + 2] == 0x00.toByte() && data[offset + 3] == 0x01.toByte())
  }

  inline fun parseAnnexBFormat(data: ByteArray, offset: Int, onNalUnit: onNalUnit): Int {
    var currentOffset = offset
    while (currentOffset < data.size) {
      val startCodePrefixLength = if (data[currentOffset + 2] == 0x01.toByte()) 3 else 4
      currentOffset += startCodePrefixLength
      val nalUnitLength = findNextStartCode(data, currentOffset) - currentOffset
      val nalUnitData = data.sliceArray(currentOffset until currentOffset + nalUnitLength)
      onNalUnit(parseNalUnit(nalUnitData, true))
      currentOffset += nalUnitLength
    }
    return currentOffset
  }

  inline fun parseAvccFormat(data: ByteArray, offset: Int, onNalUnit: onNalUnit): Int {
    var currentOffset = offset
    while (currentOffset < data.size) {
      val nalUnitLength = readNalUnitLength(data, currentOffset)
      val nalUnitData = data.sliceArray(currentOffset + 4 until currentOffset + 4 + nalUnitLength)
      onNalUnit(parseNalUnit(nalUnitData, false))
      currentOffset += 4 + nalUnitLength
    }
    return currentOffset
  }

  private fun findNextStartCode(data: ByteArray, offset: Int): Int {
    for (i in offset until data.size - 3) {
      // Check for NalStartCode.Three or NalStartCode.Four
      if (data[i] == 0x00.toByte() && data[i + 1] == 0x00.toByte() && (data[i + 2] == 0x01.toByte() || (data[i + 2] == 0x00.toByte() && data[i + 3] == 0x01.toByte()))) {
        return i
      }
    }
    return data.size
  }

  private fun readNalUnitLength(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF shl 24) or
            (data[offset + 1].toInt() and 0xFF shl 16) or
            (data[offset + 2].toInt() and 0xFF shl 8) or
            (data[offset + 3].toInt() and 0xFF)
  }

  fun parseNalUnit(data: ByteArray, isAnnexB: Boolean = false): NalUnit {
    val byte = data[0].toInt() and 0xFF
    val forbiddenZeroBit = byte shr 7
    val nalRefIdc = (byte shr 5) and 0b0000_0011
    val nalUnitType = byte and 0b0001_1111
    // copyOfRange is faster than ByteArrayOutputStream
    val rbspBytes = data.sliceArray(1 until data.size)
    return NalUnit(
      forbiddenZeroBit = forbiddenZeroBit,
      nalRefIdc = NalIdcType.valueOf(nalRefIdc),
      nalUnitType = NalUnitType.valueOf(nalUnitType),
      rbspBytes = rbspBytes,
      isAnnexB = isAnnexB
    )
  }

}