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

package github.hua0512.hls.operators

import github.hua0512.download.DownloadPathProvider
import github.hua0512.hls.data.HlsSegment
import github.hua0512.utils.logger
import io.lindstrom.m3u8.model.MediaPlaylist
import io.lindstrom.m3u8.model.MediaSegment
import io.lindstrom.m3u8.parser.MediaPlaylistParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * @author hua0512
 * @date : 2024/9/21 14:33

 */

private const val TAG = "HlsPlaylistDumper"
private val logger = logger(TAG)

private const val MEDIA_PLAYLIST_VERSION = 3
private const val MEDIA_PLAYLIST_TARGET_DURATION = 10


fun Flow<HlsSegment>.dumpPlaylist(enable: Boolean = false, pathProvider: DownloadPathProvider): Flow<HlsSegment> =
  if (!enable) this
  else flow {

    var builder: MediaPlaylist.Builder? = null

    var index = 0

    fun close() {
      builder?.build()?.let { playlist ->
        val path = pathProvider(index)
        val segments = Path(path).resolve(SEGMENTS_FOLDER)
        segments.createDirectories()
        val playlistPath = Path(path).resolve("playlist_$index.m3u8")
        logger.info("Writing playlist to $playlistPath")
        val fos = playlistPath.toFile().outputStream().buffered()
        val parser = MediaPlaylistParser()

        fos.use {
          val playListBuffer = parser.writePlaylistAsByteBuffer(playlist)
          it.write(playListBuffer.array())
        }
      }
    }

    fun initBuilder() {
      builder = MediaPlaylist.Builder()
        .version(MEDIA_PLAYLIST_VERSION)
        .targetDuration(MEDIA_PLAYLIST_TARGET_DURATION)
        .mediaSequence(index.toLong())
        .ongoing(false)
    }

    collect { value ->

      if (value is HlsSegment.EndSegment) {
        close()
        // reset
        initBuilder()
        index++
        return@collect
      }

      value as HlsSegment.DataSegment

      if (builder == null) {
        initBuilder()
      }

      builder!!.addMediaSegments(
        MediaSegment.builder()
          .uri("${SEGMENTS_FOLDER}/$index/${value.name}")
          .duration(value.duration)
          .build()
      )

      emit(value)
    }

    close()
    builder = null

  }