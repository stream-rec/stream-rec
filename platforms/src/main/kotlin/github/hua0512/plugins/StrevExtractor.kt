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

package github.hua0512.plugins

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamInfo
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.utils.Programs
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * A rust written command line tool to extract stream info from various streaming platforms.
 *
 * https://github.com/hua0512/rust-srec/blob/main/strev-cli/README.md
 * @author hua0512
 * @date : 9/10/2025 11:43 AM
 */
open class StrevExtractor(http: HttpClient, json: Json, override val url: String) : Extractor(http, json) {
  override val regexPattern: Regex = ".*".toRegex()

  internal var cachedMediaInfo: MediaInfo? = null

  override suspend fun isLive(): Result<Boolean, ExtractorError> {
    return try {
      val mediaResult = extractMediaInfo()
      if (mediaResult.isErr) {
        return Err(mediaResult.error)
      }
      val isLive = mediaResult.value.live
      if (!isLive) {
        cachedMediaInfo = null // clear cache if not live
      }
      Ok(isLive)
    } catch (e: Exception) {
      Err(ExtractorError.InvalidResponse("Failed to check live status: ${e.message}"))
    }
  }

  override suspend fun extract(): Result<MediaInfo, ExtractorError> {
    return extractMediaInfo().also {
      // clear cache after extraction to avoid stale data
      cachedMediaInfo = null
    }
  }

  private suspend fun extractMediaInfo(): Result<MediaInfo, ExtractorError> {
    // Return cached result if available
    cachedMediaInfo?.let { return Ok(it) }

    return try {
      withContext(Dispatchers.IO) {
        // Execute strev command with proper stream handling
        val process = ProcessBuilder(Programs.strev, "extract", "--url", url, "--cookies", cookies, "--output", "json")
          .redirectErrorStream(true)
          .start()

        // Read output concurrently with process execution to prevent hanging
        val outputDeferred = async {
          process.inputStream.bufferedReader().use { reader ->
            reader.readText()
          }
        }

        // Wait for both the process to complete and output to be read
        val exitCode = async {
          process.waitFor()
        }

        val output = outputDeferred.await()
        val processExitCode = exitCode.await()

        if (processExitCode != 0) {
          return@withContext Err(ExtractorError.InvalidResponse("strev command failed with exit code $processExitCode: $output"))
        }

        // Parse JSON output
        val jsonElement = json.parseToJsonElement(output)
        val mediaObject = jsonElement.jsonObject["media"]?.jsonObject
          ?: return@withContext Err(ExtractorError.InvalidResponse("No media object found in response"))

        // Extract basic media information
        val title = mediaObject["title"]?.jsonPrimitive?.content ?: ""
        val artist = mediaObject["artist"]?.jsonPrimitive?.content ?: ""
        val coverUrl = mediaObject["cover_url"]?.jsonPrimitive?.content ?: ""
        val artistUrl = mediaObject["artist_url"]?.jsonPrimitive?.content ?: ""
        val isLive = mediaObject["is_live"]?.jsonPrimitive?.boolean ?: false
        val siteUrl = mediaObject["site_url"]?.jsonPrimitive?.content ?: url

        // Extract extras (headers) and populate platform headers
        val extrasValue = mediaObject["extras"]
        val extras = (extrasValue as? JsonObject)?.mapValues { (_, value) ->
          value.jsonPrimitive.content
        } ?: emptyMap()

        // Populate platform headers from extracted extras
        populatePlatformHeaders(extras)

        // Process streams in parallel
        val streamsArray = mediaObject["streams"]?.jsonArray ?: JsonArray(emptyList())
        val streamInfos = streamsArray
          .filterNot { it is JsonNull } // Filter out null elements
          .mapNotNull { streamElement ->
            // Additional safety check
            if (streamElement !is JsonObject) return@mapNotNull null

            val streamObj = streamElement.jsonObject
            async {
              parseStreamInfo(streamObj)
            }
          }
          .awaitAll()
          .filterNotNull()

        val mediaInfo = MediaInfo(
          site = siteUrl,
          title = title,
          artist = artist,
          coverUrl = coverUrl,
          artistImageUrl = artistUrl,
          live = isLive,
          streams = streamInfos,
          extras = extras
        )

        // Cache the result
        cachedMediaInfo = mediaInfo
        Ok(mediaInfo)
      }
    } catch (e: Exception) {
      Err(ExtractorError.InvalidResponse("Process execution failed: ${e.message}"))
    }
  }

  /**
   * Populate platform headers from media extras
   * This sets headers like User-Agent, Accept, Referer, etc. that are needed for stream requests
   */
  private fun populatePlatformHeaders(extras: Map<String, String>) {
    extras.forEach { (key, value) ->
      when (key.lowercase()) {
        "user-agent" -> platformHeaders[io.ktor.http.HttpHeaders.UserAgent] = value
        "accept" -> platformHeaders[io.ktor.http.HttpHeaders.Accept] = value
        "accept-encoding" -> platformHeaders[io.ktor.http.HttpHeaders.AcceptEncoding] = value
        "accept-language" -> platformHeaders[io.ktor.http.HttpHeaders.AcceptLanguage] = value
        "referer" -> platformHeaders[io.ktor.http.HttpHeaders.Referrer] = value
        "referrer" -> platformHeaders[io.ktor.http.HttpHeaders.Referrer] = value
        "origin" -> platformHeaders[io.ktor.http.HttpHeaders.Origin] = value
        "cookie" -> platformHeaders[io.ktor.http.HttpHeaders.Cookie] = value
        "authorization" -> platformHeaders[io.ktor.http.HttpHeaders.Authorization] = value
        else -> {
          // For other headers, use the original case from the extractor
          platformHeaders[key] = value
        }
      }
    }

    logger.debug("Populated platform headers: $platformHeaders")
  }

  private fun parseStreamInfo(streamObj: JsonObject): StreamInfo? {
    return try {
      val url = streamObj["url"]?.jsonPrimitive?.content ?: return null
      val quality = streamObj["quality"]?.jsonPrimitive?.content ?: ""
      val bitrate = streamObj["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
      val priority = streamObj["priority"]?.jsonPrimitive?.intOrNull ?: 0
      val fps = streamObj["fps"]?.jsonPrimitive?.doubleOrNull ?: 0.0
      val isHeadersNeeded = streamObj["is_headers_needed"]?.jsonPrimitive?.booleanOrNull ?: false

      // Determine video format based on media_format and stream_format
      val mediaFormat = streamObj["media_format"]?.jsonPrimitive?.content ?: ""
      val streamFormat = streamObj["stream_format"]?.jsonPrimitive?.content ?: ""

      val videoFormat = when {
        mediaFormat.equals("flv", ignoreCase = true) -> VideoFormat.flv
        mediaFormat.equals("ts", ignoreCase = true) || streamFormat.equals("hls", ignoreCase = true) -> VideoFormat.hls
        mediaFormat.equals("mp4", ignoreCase = true) -> VideoFormat.mp4
        else -> VideoFormat.flv // Default fallback
      }

      val streamExtrasObj = streamObj["extras"]
      // Extract stream extras
      val streamExtras = if (streamExtrasObj != null && streamExtrasObj is JsonObject) {
        streamExtrasObj.mapValues { (_, value) ->
          when (value) {
            is JsonPrimitive -> value.content
            else -> value.toString()
          }
        }
      } else {
        emptyMap<String, String>()
      }

      StreamInfo(
        url = url,
        format = videoFormat,
        quality = quality,
        bitrate = bitrate,
        priority = priority,
        frameRate = fps,
        extras = streamExtras,
        isHeadersNeeded = isHeadersNeeded
      )
    } catch (e: Exception) {
      logger.warn("Failed to parse stream info: ${e.message}")
      null
    }
  }
}