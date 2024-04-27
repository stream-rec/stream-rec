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
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.DownloadConfig.DefaultDownloadConfig
import github.hua0512.data.event.DownloadEvent
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.StreamInfo
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.plugins.danmu.exceptions.DownloadProcessFinishedException
import github.hua0512.plugins.download.engines.BaseDownloadEngine
import github.hua0512.plugins.download.engines.FFmpegDownloadEngine
import github.hua0512.plugins.download.engines.NativeDownloadEngine
import github.hua0512.plugins.download.engines.StreamlinkDownloadEngine
import github.hua0512.plugins.event.EventCenter
import github.hua0512.utils.deleteFile
import github.hua0512.utils.nonEmptyOrNull
import github.hua0512.utils.replacePlaceholders
import github.hua0512.utils.withIORetry
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
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
import kotlin.time.DurationUnit
import kotlin.time.toDuration

abstract class Download<out T : DownloadConfig>(val app: App, val danmu: Danmu, val extractor: Extractor) {

  companion object {
    @JvmStatic
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  // downloadUrl is the url of the stream to be downloaded
  open lateinit var downloadUrl: String

  // downloadTitle is the title of the stream to be downloaded
  protected var downloadTitle: String = ""
    set(value) {
      field = formatToFriendlyFileName(value)
    }

  /**
   * The format of the downloaded file
   * @return a [VideoFormat] instance
   */
  protected lateinit var downloadFileFormat: VideoFormat

  /**
   * Current attached streamer to this download process
   */
  protected lateinit var streamer: Streamer

  /**
   * Callback triggered when the artist avatar url is updated
   */
  private var artistAvatarUrlUpdateCallback: ((String) -> Unit)? = null

  /**
   * Callback triggered when the stream title is updated
   */
  private var streamTitleUpdateCallback: ((String) -> Unit)? = null

  /**
   * The download engine used to download the stream
   */
  private lateinit var engine: BaseDownloadEngine


  suspend fun init(streamer: Streamer) {
    this.streamer = streamer
    extractor.prepare()
  }

  protected val config by lazy {
    if (streamer.templateStreamer != null) {
      /**
       * template config uses basic config [DefaultDownloadConfig]
       */
      streamer.templateStreamer!!.downloadConfig?.run {
        // build a new config using global platform values
        createDownloadConfig().also {
          it.danmu = this.danmu
          it.maxBitRate = this.maxBitRate
          it.outputFileFormat = this.outputFileFormat
          it.outputFileName = this.outputFileName
          it.outputFolder = this.outputFolder
          it.onPartedDownload = this.onPartedDownload ?: emptyList()
          it.onStreamingFinished = this.onStreamingFinished ?: emptyList()
        }
      } ?: throw IllegalArgumentException("${streamer.name} has template streamer but no download config")
    } else {
      @Suppress("UNCHECKED_CAST")
      streamer.downloadConfig as? T ?: createDownloadConfig()
    }
  }

  abstract fun createDownloadConfig(): T


  /**
   * Check if the stream should be downloaded
   * @return true if the stream should be downloaded, false otherwise
   */
  abstract suspend fun shouldDownload(): Boolean

  /**
   * Download the stream
   * @return a list of [StreamData] containing the downloaded stream data
   */
  suspend fun download(): StreamData? = supervisorScope {
    // check if downloadUrl is valid
    if (downloadUrl.isEmpty()) throw IllegalArgumentException("(${streamer.name}) downloadUrl is required")

    // download config is required and its should not be null
    val downloadConfig = streamer.templateStreamer?.downloadConfig ?: streamer.downloadConfig ?: run {
      logger.error("(${streamer.name}) download config is required")
      throw IllegalArgumentException("(${streamer.name}) download config is required")
    }

    val fileFormat = downloadConfig.outputFileFormat ?: app.config.outputFileFormat

    val fileExtension = fileFormat.name
    val cookie = downloadConfig.cookies?.nonEmptyOrNull() ?: when (streamer.platform) {
      StreamingPlatform.HUYA -> app.config.huyaConfig.cookies
      StreamingPlatform.DOUYIN -> app.config.douyinConfig.cookies
      else -> null
    }

    val isDanmuEnabled = downloadConfig.danmu ?: app.config.danmu
    logger.debug("(${streamer.name}) downloadUrl: $downloadUrl")

    val outputPath = buildOutputFilePath(downloadConfig, fileExtension)
    // check if disk space is enough
    checkDiskSpace(outputPath.parent, app.config.maxPartSize)
    // download start time
    val startTime = Clock.System.now()
    // danmu file path
    val danmuPath = outputPath.pathString.replace("$fileExtension.part", "xml")
    // check if danmu is initialized
    val danmuJob = if (isDanmuEnabled) {
      danmu.filePath = danmuPath
      async(Dispatchers.IO) {
        val status: Boolean = withIORetry(
          maxRetries = 5,
          maxDelayMillis = 30000,
          onError = { e, count -> logger.error("(${streamer.name}) danmu failed to initialize($count): $e") }) {
          initDanmu(streamer, startTime)
        }
        if (!status) {
          logger.error("(${streamer.name}) danmu failed to initialize")
          return@async
        }
        // download danmu
        danmuDownload()
      }
    } else {
      null
    }

    // progress bar
    var pb: ProgressBar? = null

    var streamData: StreamData? = StreamData(
      title = downloadTitle,
      outputFilePath = outputPath.pathString,
      danmuFilePath = if (isDanmuEnabled) danmuPath else null,
      streamerId = streamer.id
    ).also {
      it.streamer = streamer
    }

    // download engine
    engine = selectDownloadEngine().apply {
      init(
        downloadUrl,
        fileFormat,
        outputPath.pathString,
        streamData!!,
        cookie,
        Extractor.commonHeaders.toMap(),
        startTime,
        fileLimitSize = app.config.maxPartSize.run {
          if (this > 0) this else 2621440000
        },
        fileLimitDuration = app.config.maxPartDuration
      )
      onDownloadStarted {
        danmu.videoStartTime = Clock.System.now()
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
        EventCenter.sendEvent(
          DownloadEvent.DownloadStart(
            filePath = outputPath.pathString,
            url = downloadUrl,
            platform = streamer.platform
          )
        )
      }
      onDownloadProgress { size, bitrate ->
        pb?.let {
          it.stepBy(size)
          it.extraMessage = if (bitrate.isEmpty()) "Downloading..." else "bitrate: $bitrate"
          // extract numbers from string
          if (bitrate.isNotEmpty()) {
            val bitrateValue = try {
              bitrate.substring(0, bitrate.indexOf("k")).toDouble()
            } catch (e: Exception) {
              0.0
            }
            EventCenter.sendEvent(
              DownloadEvent.DownloadStateUpdate(
                filePath = outputPath.pathString,
                url = downloadUrl,
                platform = streamer.platform,
                duration = it.totalElapsed.toSeconds(),
                speed = 0.0,
                bitrate = bitrateValue,
                fileSize = it.current,
                streamerId = streamer.id
              )
            )
          }
        }
      }
      onDownloadFinished {
        pb?.close()
        EventCenter.sendEvent(
          DownloadEvent.DownloadSuccess(
            filePath = outputPath.pathString,
            url = downloadUrl,
            platform = streamer.platform,
            data = it,
            time = Clock.System.now()
          )
        )
      }
    }

    val downloadJob = async<StreamData?> { engine.run() }

    try {
      streamData = downloadJob.await()?.also {
        it.streamer = streamer
      }
    } catch (e: Exception) {
      logger.error("(${streamer.name}) download failed: $e")
      EventCenter.sendEvent(
        DownloadEvent.DownloadError(
          filePath = outputPath.pathString,
          url = downloadUrl,
          platform = streamer.platform,
          error = e
        )
      )
      // if the download is cancelled and triggered by another exception then delete the file
      if (e !is CancellationException) {
        streamData = null
      }
      // ignore other exceptions
      if (e is UnsupportedOperationException || e is IllegalArgumentException) throw e
    } finally {
      pb?.close()
      // stop danmu job
      if (isDanmuEnabled) {
        stopDanmuJob(danmuJob)
        // check if download is initialized
        if (!danmu.isInitialized.get()) {
          streamData = streamData?.copy(danmuFilePath = null)
        }
      }
      logger.debug("({}) streamData: {}", streamer.name, streamData)
      if (streamData == null) {
        logger.error("(${streamer.name}) could not download stream")
        // delete files if download failed
        deleteOutputs(outputPath, isDanmuEnabled, Path(danmuPath))
      } else {
        logger.debug("(${streamer.name}) downloaded: ${streamData.outputFilePath}")
        if (app.config.minPartSize > 0) {
          val outputFile = Path(streamData.outputFilePath)
          val fileSize = outputFile.fileSize()
          if (fileSize < app.config.minPartSize) {
            logger.error("(${streamer.name}) file size too small: $fileSize")
            deleteOutputs(outputFile, isDanmuEnabled, Path(danmuPath))
            streamData = null
          }
        }
      }
    }
    return@supervisorScope streamData
  }

  suspend fun stopDownload(): Boolean {
    return if (::engine.isInitialized) {
      engine.stopDownload()
    } else true
  }

  private fun deleteOutputs(outputPath: Path, isDanmuEnabled: Boolean, danmuPath: Path) {
    outputPath.deleteFile()
    if (isDanmuEnabled) danmuPath.deleteFile()
  }


  /**
   * Get the download engine based on type
   * @param type the type of the download engine
   * @return a [BaseDownloadEngine] instance
   */
  private fun getDownloadEngine(type: String): BaseDownloadEngine {
    // user selected engine
    return when (type) {
      "ffmpeg" -> FFmpegDownloadEngine()
      "streamlink" -> StreamlinkDownloadEngine()
      "native" -> NativeDownloadEngine(app.client)
      else -> throw UnsupportedOperationException("$type download engine not supported")
    }
  }

  /**
   * Select the download engine based on the stream type
   * @return a [BaseDownloadEngine] instance
   */
  private fun selectDownloadEngine(): BaseDownloadEngine {
    val userSelectedEngine = getDownloadEngine(app.config.engine)
    if (!downloadUrl.contains("m3u8") && userSelectedEngine is StreamlinkDownloadEngine) {
      // fallback to ffmpeg if the stream is not HLS
      logger.warn("(${streamer.name}) stream is not HLS, fallback to ffmpeg")
      return getDownloadEngine("ffmpeg")
    }
    return userSelectedEngine
  }

  private suspend fun danmuDownload() {
    logger.info("(${streamer.name}) Starting danmu download...")
    try {
      danmu.fetchDanmu()
    } catch (e: Exception) {
      // ignore if download process is finished
      if (e is DownloadProcessFinishedException || e.cause is DownloadProcessFinishedException) return
      logger.error("(${streamer.name}) danmuDownload failed: $e")
    }
  }


  private fun buildOutputFilePath(downloadConfig: DownloadConfig, fileExtension: String): Path {
    val timestamp = Clock.System.now()
    val outputFileName = (downloadConfig.outputFileName?.nonEmptyOrNull() ?: app.config.outputFileName).run {
      formatToFriendlyFileName(this.replacePlaceholders(streamer.name, downloadTitle, timestamp) + ".$fileExtension.part")
    }

    val outputFolder = (downloadConfig.outputFolder?.nonEmptyOrNull() ?: app.config.outputFolder).run {
      val str = if (endsWith(File.separator)) this else this + File.separator
      // system file separator
      str.replacePlaceholders(streamer.name, downloadTitle, timestamp)
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


  protected suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean = danmu.run {
    init(streamer, startTime).also {
      if (!it) {
        logger.error("(${streamer.name}) failed to initialize danmu")
      }
    }
  }

  private suspend fun stopDanmuJob(danmuJob: Deferred<Unit>?) {
    danmu.finish()
    try {
      danmuJob?.cancel("Download process is finished", DownloadProcessFinishedException)
      danmuJob?.join()
    } catch (e: Exception) {
      logger.error("(${streamer.name}) failed to cancel danmuJob: $e")
    }
  }

  protected fun formatToFriendlyFileName(fileName: String): String {
    return fileName.replace(Regex("[/\n\r\t\u0000\u000c`?*\\\\<>|\":]"), "_")
  }

  /**
   * Apply filters to the list of [StreamInfo]
   * @param streams the list of [StreamInfo] to be filtered
   * @return a [StreamInfo] instance
   */
  abstract suspend fun <T : DownloadConfig> T.applyFilters(streams: List<StreamInfo>): StreamInfo


  /**
   * Get the preferred stream info by applying filters of the user config
   * @param mediaInfo the [MediaInfo] instance
   * @param streamer the [Streamer] instance
   * @param userConfig the [DownloadConfig] instance
   * @return true if the stream info is available, false otherwise
   */
  protected suspend fun getStreamInfo(
    mediaInfo: MediaInfo,
    streamer: Streamer,
    userConfig: DownloadConfig,
  ): Boolean {
    updateStreamerInfo(mediaInfo, streamer)
    if (!mediaInfo.live) return false
    if (mediaInfo.streams.isEmpty()) {
      logger.info("${streamer.name} has no streams")
      return false
    }
    val finalStreamInfo = userConfig.applyFilters(mediaInfo.streams)
    downloadFileFormat = finalStreamInfo.format
    downloadUrl = finalStreamInfo.url
    return true
  }

  /**
   * Update streamer info
   * @param mediaInfo the [MediaInfo] instance
   * @param streamer the [Streamer] instance
   */
  protected fun updateStreamerInfo(mediaInfo: MediaInfo, streamer: Streamer) {
    if (mediaInfo.artistImageUrl.isNotEmpty() && mediaInfo.artistImageUrl != streamer.avatar) {
      streamer.avatar = mediaInfo.artistImageUrl
      logger.debug("(${streamer.name}) avatar: ${streamer.avatar}")
      artistAvatarUrlUpdateCallback?.invoke(mediaInfo.artistImageUrl)
    }
    if (mediaInfo.title.isNotEmpty() && mediaInfo.title != streamer.streamTitle) {
      streamer.streamTitle = mediaInfo.title
      logger.debug("(${streamer.name}) streamTitle: ${streamer.streamTitle}")
      streamTitleUpdateCallback?.invoke(mediaInfo.title)
    }
    downloadTitle = mediaInfo.title
  }

  /**
   * Set the artist avatar url update callback
   * @param callback the callback function
   */
  fun avatarUrlUpdateCallback(callback: (String) -> Unit) {
    this.artistAvatarUrlUpdateCallback = callback
  }

  /**
   * Set the stream title update callback
   * @param callback the callback function
   */
  fun descriptionUpdateCallback(callback: (String) -> Unit) {
    this.streamTitleUpdateCallback = callback
  }
}