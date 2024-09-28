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

import github.hua0512.flv.data.avc.AvcPacketType
import github.hua0512.flv.data.sound.AACPacketType
import github.hua0512.flv.data.tag.FlvAudioTagData
import github.hua0512.flv.data.tag.FlvVideoTagData
import github.hua0512.flv.data.video.FlvVideoFrameType

/**
 * Checks if the video tag data is an AVC header.
 * @return True if the video tag data is an AVC header, false otherwise.
 */
fun FlvVideoTagData.isAvcHeader(): Boolean = avcPacketType == AvcPacketType.AVC_SEQUENCE_HEADER

/**
 * Checks if the video tag data is an AVC NALU.
 * @return True if the video tag data is an AVC NALU, false otherwise.
 */
fun FlvVideoTagData.isAvcNalu(): Boolean = avcPacketType == AvcPacketType.AVC_NALU

/**
 * Checks if the video tag data is an AVC end of sequence.
 * @return True if the video tag data is an AVC end of sequence, false otherwise.
 */
fun FlvVideoTagData.isAvcEndOfSequence(): Boolean = avcPacketType == AvcPacketType.AVC_END_OF_SEQUENCE

/**
 * Checks if the video tag data is a key frame.
 * @return True if the video tag data is a key frame, false otherwise.
 */
fun FlvVideoTagData.isKeyFrame(): Boolean = frameType == FlvVideoFrameType.KEY_FRAME


/**
 * Checks if the audio tag data is an AAC header.
 * @return True if the audio tag data is an AAC header, false otherwise.
 */
fun FlvAudioTagData.isAacHeader(): Boolean = packetType == AACPacketType.SequenceHeader