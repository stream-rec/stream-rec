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

package github.hua0512.services

import github.hua0512.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.name

/**
 * File watcher service
 * @param filePath file path to watch
 * @author hua0512
 * @date : 2024/2/16 19:07
 */

class FileWatcherService(private val filePath: String) {

  private val watchService by lazy {
    FileSystems.getDefault().newWatchService()
  }

  /**
   * Watch file and emit a flow when file is modified
   * @return flow of file modification
   */
  fun watchFileFlow(): Flow<String> = flow {
    val filePath = Paths.get(filePath)
    val parentPath = filePath.parent
    logger.debug("Watching file {}", filePath)
    withContext(Dispatchers.IO) {
      parentPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
    }

    while (true) {
      val key = withContext(Dispatchers.IO) {
        watchService.take()
      }
      key.pollEvents().forEach { event ->
        if ((event.context() as Path).name == filePath.name) {
          when (event.kind()) {
            StandardWatchEventKinds.ENTRY_MODIFY -> emit("File $filePath has been modified")
          }
        }
      }
      key.reset()
    }
  }
}