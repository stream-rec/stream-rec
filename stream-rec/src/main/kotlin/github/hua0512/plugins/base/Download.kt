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

package github.hua0512.plugins.base

import github.hua0512.app.App
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.data.config.DownloadConfig
import github.hua0512.plugins.download.engines.FFmpegDownloadEngine
import github.hua0512.plugins.download.engines.NativeDownloadEngine
import github.hua0512.utils.deleteFile
import github.hua0512.utils.rename
import github.hua0512.utils.withIOContext
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.tongfei.progressbar.DelegatingProgressBarConsumer
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ofPattern
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.time.DurationUnit
import kotlin.time.toDuration

abstract class Download(val app: App, val danmu: Danmu) {

  companion object {
    @JvmStatic
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @JvmStatic
    val commonHeaders = arrayOf(
      HttpHeaders.Accept to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      HttpHeaders.AcceptLanguage to "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3",
      HttpHeaders.UserAgent to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.3029.110 Safari/537.36"
    )

  }

  // downloadUrl is the url of the stream to be downloaded
  protected lateinit var downloadUrl: String

  // downloadTitle is the title of the stream to be downloaded
  protected var downloadTitle: String = ""
    set(value) {
      field = formatToFriendlyFileName(value)
    }

  // current attached streamer
  protected lateinit var streamer: Streamer

  /**
   * The regex pattern to be used to match the streamer url
   */
  abstract val regexPattern: String


  /**
   * Check if the stream should be downloaded
   * @param streamer the streamer to be checked
   * @return true if the stream should be downloaded, false otherwise
   */
  abstract suspend fun shouldDownload(streamer: Streamer): Boolean

  /**
   * Download the stream
   * @return a list of [StreamData] containing the downloaded stream data
   */
  suspend fun download(): StreamData? = supervisorScope {
    // check if downloadUrl is valid
    if (downloadUrl.isEmpty()) throw IllegalStateException("(${streamer.name}) downloadUrl is required")

    // download config is required and its should not be null
    val downloadConfig = streamer.downloadConfig ?: run {
      logger.error("(${streamer.name}) download config is required")
      throw IllegalStateException("(${streamer.name}) download config is required")
    }

    val fileExtension = (downloadConfig.outputFileExtension ?: app.config.outputFileFormat).run { "${extension}.part" }
    val cookie = downloadConfig.cookies ?: when (streamer.platform) {
      StreamingPlatform.HUYA -> app.config.huyaConfig.cookies
      StreamingPlatform.DOUYIN -> app.config.douyinConfig.cookies
      else -> null
    }

    val isDanmuEnabled = downloadConfig.danmu ?: app.config.danmu
    logger.debug("(${streamer.name}) downloadUrl: $downloadUrl")

    // danmu exception handler
    val danmuExceptionHandler = CoroutineExceptionHandler { _, e ->
      // ignore exceptions
      logger.error("(${streamer.name}) Danmu failed: $e")
    }


    val outputPath = buildOutputFilePath(downloadConfig, fileExtension)
    // check if disk space is enough
    checkDiskSpace(outputPath.parent, app.config.maxPartSize)
    // download start time
    val startTime = Clock.System.now()

    // check if danmu is initialized
    val isDanmuInitialized = if (isDanmuEnabled) {
      try {
        initDanmu(streamer, startTime, outputPath.pathString.replace(fileExtension, "xml"))
      } catch (e: Exception) {
        logger.error("(${streamer.name}) Danmu failed to initialize: $e")
        false
      }
    } else false

    // progress bar
    var pb: ProgressBar? = null

    var streamData: StreamData? = StreamData(
      streamer = streamer,
      title = downloadTitle,
      outputFilePath = outputPath.pathString,
      danmuFilePath = if (isDanmuInitialized) danmu.filePath else null,
    )

    // download engine
    val engine = getDownloadEngine().apply {
      if (fileExtension.contains("mp4") && this is NativeDownloadEngine) {
        throw Exception("NativeEngine does not support mp4 format")
      }
      init(
        downloadUrl,
        outputPath.pathString,
        streamData!!,
        cookie,
        commonHeaders.toMap(),
        startTime,
        fileLimitSize = app.config.maxPartSize
      )
      onDownloadStarted = {
        danmu.startTime = Clock.System.now()
        danmu.enableWrite = true
        // check if the download is timed
        val isTimed = app.config.maxPartDuration != null && app.config.maxPartDuration!! > 0
        val max = if (isTimed) {
          // check if maxPartSize is set
          // bytes to kB
          app.config.maxPartSize.takeIf { it > 0 }?.div(1024) ?: (1024 * 1024 * 1024)
        } else {
          app.config.maxPartSize
        }

        // build progress bar
        pb = ProgressBarBuilder()
          .setTaskName(streamer.name)
          .setConsumer(DelegatingProgressBarConsumer(logger::info))
          .setInitialMax(max)
          .setUpdateIntervalMillis(2.toDuration(DurationUnit.MINUTES).inWholeMilliseconds.toInt())
          .continuousUpdate()
          .hideEta()
          .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
          .build()
      }
      onDownloadProgress = { size, bitrate ->
        pb?.let {
          it.stepBy(size)
          it.extraMessage = if (bitrate.isEmpty()) "Downloading..." else "bitrate: $bitrate"
        }
      }
    }

    // danmu download job, if danmu is enabled,
    // make sure danmuJob won't cancel before downloadJob and any exceptions ocurred in danmuJob won't cancel the parent job
    val danmuJob = if (isDanmuInitialized) launch(danmuExceptionHandler) { danmuDownload() } else null

    val downloadJob = async<StreamData?> { engine.run() }

    try {
      streamData = downloadJob.await()
    } catch (e: Exception) {
      logger.error("(${streamer.name}) download failed: $e")
    }

    pb?.close()

    if (isDanmuInitialized) {
      danmu.finish()
      try {
        danmuJob?.cancel("Download process is finished")
      } catch (e: Exception) {
        logger.error("(${streamer.name}) failed to cancel danmuJob: $e")
      }
    }

    logger.debug("({}) streamData: {}", streamer.name, streamData)
    if (streamData == null) {
      logger.error("(${streamer.name}) could not download stream")
      // delete files if download failed
      outputPath.deleteFile()
      if (isDanmuInitialized) danmu.danmuFile.deleteFile()
      return@supervisorScope null
    } else {
      logger.debug("(${streamer.name}) downloaded: ${streamData.outputFilePath}")
      if (app.config.minPartSize > 0) {
        val fileSize = outputPath.toFile().length()
        if (fileSize < app.config.minPartSize) {
          logger.error("(${streamer.name}) file size too small: $fileSize")
          outputPath.deleteFile()
          if (isDanmuInitialized) danmu.danmuFile.deleteFile()
          return@supervisorScope null
        }
      }
      outputPath.rename(Path(outputPath.pathString.removeSuffix(".part")))
    }

    logger.debug("(${streamer.name}) finished download")

    return@supervisorScope streamData
  }


  private fun getDownloadEngine() = when (app.config.engine) {
    "ffmpeg" -> FFmpegDownloadEngine(app)
    "native" -> NativeDownloadEngine(app)
    else -> throw Exception("Engine not supported")
  }

  private suspend fun danmuDownload() {
    logger.info("(${streamer.name}) Starting danmu download...")
    danmu.fetchDanmu()
  }


  private fun buildOutputFilePath(downloadConfig: DownloadConfig, fileExtension: String): Path {
    val localeDate = LocalDateTime.now().run {
      format(ofPattern("yyyy-MM-dd HH-mm-ss"))
    }
    // replace placeholders in the output file name
    val toReplace = mapOf(
      "{streamer}" to streamer.name,
      "{title}" to downloadTitle,
      "%yyyy" to localeDate.substring(0, 4),
      "%MM" to localeDate.substring(5, 7),
      "%dd" to localeDate.substring(8, 10),
      "%HH" to localeDate.substring(11, 13),
      "%mm" to localeDate.substring(14, 16),
      "%ss" to localeDate.substring(17, 19),
    )
    val outputFileName = (if (downloadConfig.outputFileName.isNullOrEmpty()) {
      app.config.outputFileName
    } else {
      downloadConfig.outputFileName!!
    }).run {
      formatToFriendlyFileName(toReplace.entries.fold(this) { acc, entry ->
        acc.replace(entry.key, entry.value)
      } + ".$fileExtension")
    }

    val outputFolder = (if (downloadConfig.outputFolder.isNullOrEmpty()) {
      app.config.outputFolder
    } else {
      downloadConfig.outputFolder!!
    }).run {
      val str = if (endsWith(File.separator)) this else this + File.separator
      // system file separator
      toReplace.entries.fold(str) { acc, entry ->
        acc.replace(entry.key, entry.value)
      }
    }
    val sum = outputFolder + outputFileName

    return Path(sum).also {
      Files.createDirectories(it.parent)
      Files.exists(it).let { exists ->
        if (exists) {
          logger.error("(${sum}) file already exists")
          throw IllegalStateException("File already exists")
        }
      }
    }
  }

  private fun checkDiskSpace(path: Path, segmentPart: Long) {
    val fileStore = Files.getFileStore(path)
    val usableSpace = fileStore.usableSpace
    if (usableSpace < segmentPart) {
      logger.error("Not enough disk space: $usableSpace")
      throw IllegalStateException("Not enough disk space")
    }
  }


  protected suspend fun initDanmu(streamer: Streamer, startTime: Instant, filePath: String): Boolean =
    withIOContext {
      danmu.init(streamer, startTime).run {
        if (this) {
          logger.info("(${streamer.name}) Danmu initialized")
          danmu.filePath = filePath
        } else {
          logger.error("${streamer.name}) Danmu failed to initialize")
        }
        this
      }
    }


  protected fun formatToFriendlyFileName(fileName: String): String {
    return fileName.replace(Regex("[/\n\r\t\u0000\u000c`?*\\\\<>|\":]"), "_")
  }
}