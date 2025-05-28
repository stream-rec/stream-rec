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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.nio.file.*
import kotlin.io.path.name

/**
 * This class provides a service to watch a specified file for modifications.
 * It uses the Java NIO WatchService API to monitor the file system for changes.
 *
 * @property filePath The path of the file to watch.
 * @constructor Creates a new FileWatcherService with the specified file path.
 */
@Deprecated("Not used anymore")
class FileWatcherService(private val filePath: String) {

  /**
   * The WatchService used to monitor the file system.
   */
  private val watchService by lazy {
    FileSystems.getDefault().newWatchService()
  }

  /**
   * A flow that emits events when the file is modified.
   */
  val eventFlow = MutableSharedFlow<WatchEvent.Kind<Path>>()

  /**
   * Starts watching the file for modifications.
   */
  suspend fun watchFileModifications() {
    val filePath = Paths.get(filePath)
    val parentPath = filePath.parent
    github.hua0512.utils.mainLogger.debug("Watching file {}", filePath)
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
            StandardWatchEventKinds.ENTRY_MODIFY -> eventFlow.emit(StandardWatchEventKinds.ENTRY_MODIFY)
          }
        }
      }
      key.reset()
    }
  }

  /**
   * Closes the WatchService.
   */
  fun close() {
    eventFlow.resetReplayCache()
    watchService.close()
  }
}