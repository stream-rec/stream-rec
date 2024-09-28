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
import java.io.ByteArrayInputStream

internal object AVCSequenceHeaderParser {

  fun parse(data: ByteArray): AVCDecoderConfigurationRecord {
    val reader = StructReader(ByteArrayInputStream(data))
    reader.use {
      val configurationVersion = reader.readUI8()
      val avcProfileIndication = reader.readUI8()
      val profileCompatibility = reader.readUI8()
      val avcLevelIndication = reader.readUI8()
      val lengthSizeMinusOne = reader.readUI8() and 0b11

      val numOfSequenceParameterSets = reader.readUI8() and 0b11111
      val sequenceParameterSets = mutableListOf<SequenceParameterSet>()

      repeat(numOfSequenceParameterSets) {
        val sequenceParameterSetLength = reader.readUI16()
        val sequenceParameterSetNalUnit = reader.readBytes(sequenceParameterSetLength)
        sequenceParameterSets.add(
          SequenceParameterSet(
            sequenceParameterSetLength,
            sequenceParameterSetNalUnit
          )
        )
      }

      val numOfPictureParameterSets = reader.readUI8()
      val pictureParameterSets = mutableListOf<PictureParameterSet>()

      repeat(numOfPictureParameterSets) {
        val pictureParameterSetLength = reader.readUI16()
        val pictureParameterSetNalUnit = reader.readBytes(pictureParameterSetLength)
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