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

package github.hua0512.plugins.download.engines

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val KOTLIN_ENGINE = "kotlin"
private const val FFMPEG_ENGINE = "ffmpeg"
private const val STREAMLINK_ENGINE = "streamlink"

/**
 * Download engines
 * @property engine the engine name
 * @author hua0512
 * @date : 2024/10/10 21:34
 */
@Serializable
sealed class DownloadEngines(val engine: String) {

  @Serializable
  @SerialName(KOTLIN_ENGINE)
  public data object KOTLIN : DownloadEngines(KOTLIN_ENGINE)

  @Serializable
  @SerialName(FFMPEG_ENGINE)
  public data object FFMPEG : DownloadEngines(FFMPEG_ENGINE)

  @Serializable
  @SerialName(STREAMLINK_ENGINE)
  public data object STREAMLINK : DownloadEngines(STREAMLINK_ENGINE)


  public companion object {

    public fun fromString(engine: String): DownloadEngines {
      return when (engine) {
        KOTLIN_ENGINE -> KOTLIN
        FFMPEG_ENGINE -> FFMPEG
        STREAMLINK_ENGINE -> STREAMLINK
        else -> throw IllegalArgumentException("Unknown engine type: $engine")
      }
    }
  }

}