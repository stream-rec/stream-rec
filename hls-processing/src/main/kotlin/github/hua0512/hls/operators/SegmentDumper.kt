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
import github.hua0512.hls.data.HlsSegment.DataSegment
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

private const val TAG = "HlsDumper"
private val logger by lazy { logger(TAG) }
internal const val SEGMENTS_FOLDER = "segments"

/**
 * Extension function to dump HLS segments to files.
 *
 * @receiver Flow<HlsSegment> The flow of HLS segments to be dumped.
 * @param context StreamerContext The context of the streamer.
 * @param pathProvider DownloadPathProvider Provides the path for downloading segments.
 * @param combineOneFile Boolean Whether to combine all segments into one file. Default is true.
 * @param onDownloadStarted OnDownloadStarted? Callback invoked when download starts. Default is null.
 * @param onDownloaded OnDownloaded Callback invoked when download completes. Default is a no-op.
 * @return Flow<HlsSegment> The flow of HLS segments after dumping.
 */
internal fun Flow<HlsSegment>.dump(
  context: StreamerContext,
  pathProvider: DownloadPathProvider,
  combineOneFile: Boolean = true,
  onDownloadStarted: OnDownloadStarted? = null,
  onDownloaded: OnDownloaded = { _, _, _, _ -> Unit },
): Flow<HlsSegment> = flow {

  var index = 0
  var lastPath: String? = null
  var lastOpenTime = 0L
  var writer: RandomAccessFile? = null

  /**
   * Initializes the file for writing the segment.
   *
   * @param path String The path of the file.
   * @param segment DataSegment The data segment to be written.
   */
  fun init(path: String, segment: DataSegment) {
    val file = if (!combineOneFile) {
      val origPath = Path(path)
      val segmentsFolder = origPath.resolve("$SEGMENTS_FOLDER/${index}")
      segmentsFolder.createDirectories()
      segmentsFolder.resolve(segment.name)
    } else {
      onDownloadStarted?.invoke(path, System.currentTimeMillis())
      // make sure path extension is the same as the segment
      val ext = segment.name.substringAfterLast(".")
      if (ext == "m4s") {
        "mp4"
      } else {
        ext
      }
      val jPath = Path(path)
      val path = jPath.resolveSibling("${jPath.nameWithoutExtension}.$ext")
      Files.createFile(path)
    }
    logger.info("${context.name} Writing to: {}", file)
    writer = RandomAccessFile(file.toFile(), "rw").also {
      it.setLength(0)
    }
    lastPath = file.pathString
    lastOpenTime = System.currentTimeMillis()
  }

  /**
   * Closes the current file writer.
   */
  fun close() {
    writer?.also {
      it.close()
      if (combineOneFile) {
        onDownloaded(index, lastPath!!, lastOpenTime, System.currentTimeMillis())
      }
    }
  }

  /**
   * Resets the writer by closing the current file writer and setting it to null.
   */
  fun reset() {
    close()
    writer = null
  }

  collect {

    if (it is HlsSegment.EndSegment) {
      reset()
      emit(it)

      if (!combineOneFile) {
        index++
      }
      return@collect
    }

    it as DataSegment

    if (!combineOneFile) {
      close()
      val path = pathProvider(index)
      init(path, it)
    } else if (writer == null) {
      reset()
      val path = pathProvider(index)
      init(path, it)
      index++
    }

    writer?.write(it.data)

    emit(it)
  }

  reset()
  logger.debug("${context.name} end")
}