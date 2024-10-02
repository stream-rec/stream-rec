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

package github.hua0512.flv.operators

import github.hua0512.download.DownloadPathProvider
import github.hua0512.download.OnDownloaded
import github.hua0512.flv.FlvWriter
import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvHeader
import github.hua0512.flv.data.FlvTag
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.datetime.Clock
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream


private const val TAG = "FlvDumperCenter"
private val logger = logger(TAG)


/**
 * Dump flv data to file by path provider.
 * @author hua0512
 * @date : 2024/9/9 2:15
 */
fun Flow<FlvData>.dump(pathProvider: DownloadPathProvider, onStreamDumped: OnDownloaded = { _, _, _, _ -> }): Flow<FlvData> = flow {

  var writer: FlvWriter? = null
  var lastPath: String? = null
  var streamIndex = -1
  var lastOpenTime = 0L

  fun init(path: String) {
    var jPath = Path(path)
    // force to use "flv" extension
    if (jPath.extension != "flv") {
      jPath = jPath.resolveSibling("${jPath.nameWithoutExtension}.flv")
    }
    val file = Files.createFile(jPath)
    logger.info("Starting write to: {}", file)
    writer = FlvWriter(file.outputStream().buffered())
    lastPath = path
    lastOpenTime = Clock.System.now().toEpochMilliseconds()
  }

  fun close() {
    writer?.close()
  }

  fun reset() {
    writer = null
    lastPath = null
    lastOpenTime = 0
    streamIndex = -1
  }

  fun closeAndInform() {
    // close the previous writer
    close()
    // inform that previous stream is dumped
    lastPath?.let {
      onStreamDumped(streamIndex, it, lastOpenTime, Clock.System.now().toEpochMilliseconds())
    }
  }


  fun FlvHeader.write() {
    streamIndex++
    if (streamIndex > 0)
      logger.debug("Split flv file...")
    // close the previous writer and inform
    closeAndInform()

    // create a new writer
    init(pathProvider(streamIndex))
    writer?.writeHeader(this)
  }

  onCompletion {
    closeAndInform()
    reset()
  }.collect { value ->

    if (value is FlvHeader) {
      value.write()
    } else {
      value as FlvTag
      writer?.writeTag(value)
    }

    emit(value)
  }

}