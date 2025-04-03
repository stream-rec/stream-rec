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

import github.hua0512.app.App
import github.hua0512.data.dto.IOutputFile
import github.hua0512.data.plugin.PluginConfigs
import github.hua0512.data.plugin.PluginConfigs.CopyFileConfig
import github.hua0512.data.plugin.PluginError
import github.hua0512.data.stream.StreamData
import github.hua0512.plugins.action.PluginPipeline
import github.hua0512.plugins.action.ProcessingPlugin
import github.hua0512.plugins.command.SimpleShellCommandPlugin
import github.hua0512.plugins.ffmpeg.RemuxPlugin
import github.hua0512.plugins.file.CopyFilePlugin
import github.hua0512.plugins.file.DeleteFilePlugin
import github.hua0512.plugins.file.MoveFilePlugin
import github.hua0512.plugins.upload.RcloneUploadPlugin
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.upload.UploadRepo
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Service responsible for executing action pipelines
 * @author hua0512
 */
class ActionService(
  private val app: App,
  private val streamDataRepo: StreamDataRepo,
  private val uploadRepo: UploadRepo,
) {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(ActionService::class.java)
  }

  private lateinit var uploadSemaphore: Semaphore

  /**
   * Execute a list of actions on the given stream data
   * Each action list is treated as a separate pipeline
   * @param streamDataList The stream data to process
   * @param pluginConfigs The list of plugin configs to execute
   * @return List of pipeline results
   */
  suspend fun runActions(
    streamDataList: List<StreamData>,
    pluginConfigs: List<PluginConfigs>,
  ) = withIOContext {
    if (pluginConfigs.isEmpty()) {
      logger.debug("No actions to execute")
      return@withIOContext
    }


    val initialData = mutableListOf<IOutputFile>()
    streamDataList.forEach {
      initialData.add(it)
      // separate the danmu file from the stream data
      if (it.danmuFilePath != null) {
        initialData.add(object : IOutputFile {
          override var path: String = it.danmuFilePath!!
          override var size: Long = File(path).length()
          override var streamerName: String? = it.streamerName
          override var streamerPlatform: String? = it.streamerPlatform
          override var streamTitle: String? = it.streamTitle
          override var streamDate: Long? = it.streamDate
          override var streamDataId: Long = it.streamDataId
        })
      }
    }
    val plugins = mutableListOf<ProcessingPlugin<*, *, *>>()

    pluginConfigs.forEach {
      when (it) {
        is CopyFileConfig -> {
          val copyPlugin = CopyFilePlugin(it)
          plugins.add(copyPlugin)
        }

        is PluginConfigs.MoveFileConfig -> {
          val movePlugin = MoveFilePlugin(it)
          plugins.add(movePlugin)
        }

        is PluginConfigs.DeleteFileConfig -> {
          val deletePlugin = DeleteFilePlugin(it)
          plugins.add(deletePlugin)
        }

        is PluginConfigs.SimpleShellCommandConfig -> {
          plugins.add(SimpleShellCommandPlugin(it))
        }

        is PluginConfigs.UploadConfig.RcloneConfig -> {
          if (!::uploadSemaphore.isInitialized) {
            uploadSemaphore = Semaphore(app.config.maxConcurrentUploads)
          }
          plugins.add(RcloneUploadPlugin(it, uploadSemaphore, streamDataRepo, uploadRepo))
        }

        is PluginConfigs.RemuxConfig -> {
          plugins.add(RemuxPlugin(it))
        }

        else -> {
          logger.warn("Unsupported plugin type: ${it::class.simpleName}")
        }
      }
    }

    @Suppress("UNCHECKED_CAST")
    val pipeline = PluginPipeline(plugins = plugins as List<ProcessingPlugin<IOutputFile, IOutputFile, PluginError>>)
    val pipelineResult = pipeline.execute(initialData)
    if (pipelineResult.isErr) {
      logger.error("Pipeline execution failed: ${pipelineResult.error}")
    }
  }

}