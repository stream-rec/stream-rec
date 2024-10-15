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

package github.hua0512.flv.utils

import github.hua0512.flv.data.avc.AVCSequenceHeaderParser
import github.hua0512.flv.data.avc.nal.NalUnitParser
import github.hua0512.flv.data.avc.nal.SequenceParameterSetRBSPParser
import github.hua0512.flv.data.video.VideoResolution
import github.hua0512.flv.exceptions.FlvDataErrorException

/**
 * Extracts the resolution of a video stream from an AVC sequence header.
 *
 * @param packet The packet containing the AVC sequence header.
 * @return The resolution of the video stream. The first element is the width, and the second element is the height.
 * @author hua0512
 * @date : 2024/6/10 13:03
 */
internal fun extractResolution(packet: ByteArray): VideoResolution {
  val record = AVCSequenceHeaderParser.parse(packet)
  if (record.numOfSequenceParameterSets < 1 || record.sequenceParameterSets.isEmpty()) {
    throw FlvDataErrorException("No sequence parameter sets found in the AVC sequence header.")
  }
  val sps = record.sequenceParameterSets.first()
  val nalUnit = NalUnitParser.parseNalUnit(sps.nalUnit)
  val spsData = SequenceParameterSetRBSPParser.parse(nalUnit.rbspBytes)
  return VideoResolution(spsData.frameWidth, spsData.frameHeight)
}