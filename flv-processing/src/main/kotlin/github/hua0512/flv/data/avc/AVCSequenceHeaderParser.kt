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

package github.hua0512.flv.data.avc

import github.hua0512.flv.data.avc.nal.PictureParameterSet
import github.hua0512.flv.data.avc.nal.SequenceParameterSet
import github.hua0512.flv.exceptions.FlvDataErrorException
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readUByte
import kotlinx.io.readUShort
import java.io.ByteArrayInputStream

internal class AVCSequenceHeaderParser {

  companion object {
    fun parse(data: ByteArray): AVCDecoderConfigurationRecord {
      val reader = ByteArrayInputStream(data).asSource().buffered()

      try {
        reader.require(5)
      } catch (e: Throwable) {
        throw FlvDataErrorException("AVCDecoderConfigurationRecord data is too short")
      }

      reader.use { source ->
        val configurationVersion = source.readUByte()
        val avcProfileIndication = source.readUByte()
        val profileCompatibility = source.readUByte()
        val avcLevelIndication = source.readUByte()
        val lengthSizeMinusOne = source.readUByte() and 3u

        val numOfSequenceParameterSets = source.readUByte().toInt() and 0b11111
        val sequenceParameterSets = mutableListOf<SequenceParameterSet>()

        repeat(numOfSequenceParameterSets.toInt()) {
          val sequenceParameterSetLength = source.readUShort().toInt()
          val sequenceParameterSetNalUnit = source.readByteArray(sequenceParameterSetLength)
          sequenceParameterSets.add(
            SequenceParameterSet(
              sequenceParameterSetLength,
              sequenceParameterSetNalUnit
            )
          )
        }

        val numOfPictureParameterSets = source.readUByte().toInt()
        val pictureParameterSets = mutableListOf<PictureParameterSet>()

        repeat(numOfPictureParameterSets) {
          val pictureParameterSetLength = source.readUShort().toInt()
          val pictureParameterSetNalUnit = source.readByteArray(pictureParameterSetLength)
          pictureParameterSets.add(
            PictureParameterSet(
              pictureParameterSetLength,
              pictureParameterSetNalUnit
            )
          )
        }
        return AVCDecoderConfigurationRecord(
          configurationVersion,
          avcProfileIndication,
          profileCompatibility,
          avcLevelIndication,
          lengthSizeMinusOne,
          numOfSequenceParameterSets,
          sequenceParameterSets,
          numOfPictureParameterSets,
          pictureParameterSets
        )
      }
    }
  }
}