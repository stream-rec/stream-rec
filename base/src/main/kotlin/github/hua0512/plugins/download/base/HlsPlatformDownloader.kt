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

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.dto.platform.HlsPlatformConfigDTO
import github.hua0512.data.platform.HlsQuality
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.utils.debug
import github.hua0512.utils.warn

/**
 * @author hua0512
 * @date : 2024/12/4 21:51
 */
abstract class HlsPlatformDownloader<T : DownloadConfig>(
  app: App,
  danmu: Danmu,
  override val extractor: Extractor,
) : PlatformDownloader<T>(app, danmu, extractor) {

  override suspend fun applyFilters(streams: List<StreamInfo>): Result<StreamInfo, ExtractorError> {
    val config = downloadConfig as? HlsPlatformConfigDTO ?: return Err(ExtractorError.InitializationError(Throwable("Invalid config type")))

    val userPreferredQuality = config.quality ?: HlsQuality.Source
    // source quality should be the first one
    if (userPreferredQuality == HlsQuality.Source) {
      return getBestAvailableStream(streams)
    } else if (userPreferredQuality == HlsQuality.Audio) {
      val audioStream = streams.firstOrNull { it.quality.contains("audio") }
      if (audioStream != null) {
        return Ok(audioStream)
      }
      warn("No audio stream found, using the best available")
      return getBestAvailableStream(streams)
    }
    // resolution quality
    val preferredResolution = userPreferredQuality.value.removeSuffix("p").toInt()
    // filter by user defined quality
    val selectedStream = streams.filter { it.quality.contains(userPreferredQuality.value) }
    if (selectedStream.isEmpty()) {
      // if no stream found, return the first lower quality than user defined quality
      val filteredStreams =
        streams.map { (it.extras["resolution"].toString().split("x").last().toIntOrNull() ?: 0) to it }
          .filter {
            it.first < preferredResolution
          }.maxByOrNull {
            it.first
          }?.second?.apply {
            warn("No stream found with quality {}, using {} instead", userPreferredQuality, this.quality)
          } ?: run {
          warn("No stream found with quality {}, using the best available", userPreferredQuality)
          streams.first()
        }
      return Ok(filteredStreams)
    }

    // there is a stream with the user defined quality
    // could it be the one with 30 or 60 fps
    // so we select the one with the highest frame rate
    val filteredStream = selectedStream.maxBy {
      it.frameRate
    }
    debug("selected stream: {}", filteredStream)
    return Ok(filteredStream)

  }


  private fun getBestAvailableStream(streams: List<StreamInfo>): Result<StreamInfo, ExtractorError> {
    val sourceStream = streams.find { it.quality == "source" }
    return if (sourceStream != null) {
      debug("Using source quality stream: {}", sourceStream)
      Ok(sourceStream)
    } else {
      Ok(streams.maxBy { it.bitrate })
    }
  }

}