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
import github.hua0512.utils.slogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString


private const val TAG = "HlsDumper"

internal const val SEGMENTS_FOLDER = "segments"

/**
 * @author hua0512
 * @date : 2024/9/19 22:30
 */

internal fun Flow<HlsSegment>.dump(
  context: StreamerContext,
  pathProvider: DownloadPathProvider,
  combineOneFile: Boolean = true,
  onDownloadStarted: OnDownloadStarted? = null,
  onDownloaded: OnDownloaded = { _, _, _, _ -> Unit },
): Flow<HlsSegment> = flow<HlsSegment> {

  val logger = context.slogger(TAG)

  var index = 0
  var lastPath: String? = null
  var lastOpenTime = 0L
  var writer: RandomAccessFile? = null

  fun init(path: String, segment: DataSegment) {
    val file = if (!combineOneFile) {
      val origPath = Path(path)
      val segmentsFolder = origPath.resolve("$SEGMENTS_FOLDER/${index}")
      segmentsFolder.createDirectories()
      segmentsFolder.resolve(segment.name)
    } else {
      onDownloadStarted?.invoke(path, System.currentTimeMillis())
      Files.createFile(Path(path))
    }
    logger.info("Writing to: {}", file)
    writer = RandomAccessFile(file.toFile(), "rw").also {
      it.setLength(0)
    }
    lastPath = file.pathString
    lastOpenTime = System.currentTimeMillis()
  }


  fun close() {
    writer?.also {
      it.close()
      if (combineOneFile) {
        onDownloaded(index, lastPath!!, lastOpenTime, System.currentTimeMillis())
      }
    }
  }

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
  logger.debug("$TAG end")
}