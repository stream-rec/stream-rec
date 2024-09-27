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
import github.hua0512.download.OnDownloadStarted
import github.hua0512.download.OnDownloaded
import github.hua0512.hls.data.HlsSegment
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import io.lindstrom.m3u8.model.MediaPlaylist
import io.lindstrom.m3u8.model.MediaSegment
import io.lindstrom.m3u8.parser.MediaPlaylistParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

private const val TAG = "HlsPlaylistDumper"
private val logger by lazy { logger(TAG) }
private const val MEDIA_PLAYLIST_VERSION = 3
private const val MEDIA_PLAYLIST_TARGET_DURATION = 10

/**
 * Extension function to dump HLS segments into a media playlist.
 *
 * @param context The context of the streamer.
 * @param enable Flag to enable or disable the dumping process.
 * @param pathProvider Function to provide the download path.
 * @param onDownloadStarted Callback invoked when the download starts.
 * @param onDownloaded Callback invoked when the download is completed.
 * @return A Flow of HlsSegment.
 */
internal fun Flow<HlsSegment>.dumpPlaylist(
  context: StreamerContext,
  enable: Boolean = false,
  pathProvider: DownloadPathProvider,
  onDownloadStarted: OnDownloadStarted? = null,
  onDownloaded: OnDownloaded,
): Flow<HlsSegment> =
  if (!enable) this
  else flow {
    var builder: MediaPlaylist.Builder? = null
    var index = 0
    var lastTime = 0L
    var lastPath: String? = null

    /**
     * Closes the current playlist by writing it to a file.
     */
    fun close() {
      builder?.build()?.let { playlist ->
        logger.info("${context.name} Writing playlist to $lastPath")
        val fos = Path(lastPath!!).toFile().outputStream().buffered()
        val parser = MediaPlaylistParser()

        fos.use {
          val playListBuffer = parser.writePlaylistAsByteBuffer(playlist)
          it.write(playListBuffer.array())
        }
        onDownloaded(index, lastPath!!, lastTime, System.currentTimeMillis())
      }
      builder = null
    }

    /**
     * Resets the builder and related variables.
     */
    fun reset() {
      builder = null
      index = 0
      lastTime = 0L
      lastPath = null
    }

    /**
     * Initializes the MediaPlaylist builder.
     */
    fun initBuilder() {
      builder = MediaPlaylist.Builder()
        .version(MEDIA_PLAYLIST_VERSION)
        .targetDuration(MEDIA_PLAYLIST_TARGET_DURATION)
        .mediaSequence(index.toLong())
        .ongoing(false)
      lastTime = System.currentTimeMillis()
      val path = pathProvider(index)
      val segments = Path(path).resolve(SEGMENTS_FOLDER)
      segments.createDirectories()
      val playlistPath = Path(path).resolve("PART_playlist_${index}_${System.currentTimeMillis()}.m3u8")
      lastPath = playlistPath.pathString
      onDownloadStarted?.invoke(lastPath!!, lastTime)
    }

    collect { value ->
      if (value is HlsSegment.EndSegment) {
        close()
        index++
        initBuilder()
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
    reset()
    logger.debug("${context.name} end")
  }