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

package github.hua0512.plugins.download.base

import github.hua0512.data.config.engine.DownloadEngines
import github.hua0512.data.media.VideoFormat
import github.hua0512.plugins.download.engines.BaseDownloadEngine
import github.hua0512.plugins.download.engines.ffmpeg.FFmpegDownloadEngine
import github.hua0512.plugins.download.engines.ffmpeg.StreamlinkDownloadEngine
import github.hua0512.plugins.download.engines.kotlin.KotlinFlvDownloadEngine
import github.hua0512.plugins.download.engines.kotlin.KotlinHlsDownloadEngine

/**
 * Factory for creating download engines
 * @see BaseDownloadEngine
 * @see DownloadEngines
 * @author hua0512
 * @date : 2024/10/10 21:33
 */
class DownloadEngineFactory {

  companion object {
    fun createEngine(engineType: DownloadEngines, videoFormat: VideoFormat): BaseDownloadEngine {
      return when (engineType) {
        DownloadEngines.KOTLIN -> {
          if (videoFormat == VideoFormat.flv) {
            KotlinFlvDownloadEngine()
          } else if (videoFormat == VideoFormat.hls) {
            KotlinHlsDownloadEngine()
          } else {
            throw IllegalArgumentException("Kotlin engine only supports FLV and HLS format")
          }
        }

        DownloadEngines.FFMPEG -> FFmpegDownloadEngine()
        DownloadEngines.STREAMLINK -> StreamlinkDownloadEngine()
        else -> throw IllegalArgumentException("Unknown engine type: $engineType")
      }
    }
  }

}