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

package github.hua0512.plugins.download.base

import github.hua0512.app.App
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.DownloadConfig.DefaultDownloadConfig
import github.hua0512.data.event.DownloadEvent
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.*
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.download.engines.BaseDownloadEngine
import github.hua0512.plugins.download.engines.BaseDownloadEngine.Companion.PART_PREFIX
import github.hua0512.plugins.download.engines.FFmpegDownloadEngine
import github.hua0512.plugins.download.engines.StreamlinkDownloadEngine
import github.hua0512.plugins.download.exceptions.DownloadFilePresentException
import github.hua0512.plugins.download.exceptions.InsufficientDownloadSizeException
import github.hua0512.plugins.download.exceptions.InvalidDownloadException
import github.hua0512.plugins.event.EventCenter
import github.hua0512.utils.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
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

abstract class Download<out T : DownloadConfig>(val app: App, open val danmu: Danmu, open val extractor: Extractor) {

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

  /**
   * Callback triggered when the stream is downloaded
   */
  private var onStreamDownloaded: ((StreamData) -> Unit)? = null

  /**
   * Callback triggered when the stream download fails
   */
  private var onStreamDownloadError: ((Exception) -> Unit)? = null


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
  suspend fun download() = supervisorScope {
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
      StreamingPlatform.DOUYU -> app.config.douyuConfig.cookies
      StreamingPlatform.TWITCH -> app.config.twitchConfig.cookies
      StreamingPlatform.PANDALIVE -> app.config.pandaliveConfig.cookies
      else -> null
    }

    val isDanmuEnabled = downloadConfig.danmu ?: app.config.danmu
    logger.debug("(${streamer.name}) downloadUrl: $downloadUrl")

    // build output file path
    val outputPath = buildOutputFilePath(downloadConfig, fileExtension)
    // check if disk space is enough
    checkDiskSpace(outputPath.parent, app.config.maxPartSize)
    // download start time
    val startTime = Clock.System.now()
    // check if danmu is initialized
    var danmuJob: Job? = null
    // progress bar
    var pb: ProgressBar? = null

    val streamData: StreamData = StreamData(
      title = downloadTitle,
      outputFilePath = outputPath.pathString,
      danmuFilePath = null,
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
        streamer,
        cookie,
        Extractor.commonHeaders.toMap(),
        fileLimitSize = app.config.maxPartSize.run {
          if (this > 0) this else 2621440000
        },
        fileLimitDuration = app.config.maxPartDuration,
        callback = object : DownloadCallback {
          override fun onInit() {
            logger.debug("(${streamer.name}) download initialized")
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

          override fun onDownloadStarted(filePath: String, time: Long) {
            logger.debug("(${streamer.name}) download started : $filePath, time: $time")
            pb?.reset()
            val danmuPath = filePath.replace(fileExtension, ContentType.Application.Xml.contentSubtype).replace(PART_PREFIX, "")
            danmu.videoStartTime = Instant.fromEpochSeconds(time)
            danmu.filePath = danmuPath

            if (isDanmuEnabled) {
              if (danmuJob == null) {
                danmuJob = async(Dispatchers.IO) {
                  val status: Boolean = withIORetry(
                    maxRetries = 5,
                    maxDelayMillis = 30000,
                    onError = { e, count -> logger.error("(${streamer.name}) danmu failed to initialize($count): $e") }) {
                    initDanmu(streamer, startTime)
                  }
                  if (!status) {
                    logger.error("(${streamer.name}) danmu failed to initialize")
                    return@async
                  } else {
                    logger.debug("(${streamer.name}) danmu :${danmu.filePath} initialized")
                  }
                  danmu.enableWrite = true
                  danmuDownload()
                }
              } else {
                danmu.enableWrite = true
                danmu.relaunchIO(danmuPath)
              }
            }
            EventCenter.sendEvent(
              DownloadEvent.DownloadStart(
                filePath = filePath,
                url = downloadUrl,
                platform = streamer.platform
              )
            )
          }

          override fun onDownloadProgress(diff: Long, bitrate: String) {
            pb?.let {
              it.stepBy(diff)
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

          override fun onDownloaded(data: FileInfo) {
            logger.debug("({}) downloaded: {}", streamer.name, data)
            // check if the segment is valid
            danmu.finish()
            val danmuPath = if (isDanmuEnabled) Path(danmu.filePath) else null
            logger.debug("(${streamer.name}) danmu finished : ${danmu.filePath}")
            if (processSegment(Path(data.path), danmuPath)) return
            // update stream data
            val stream = streamData.copy(
              dateStart = data.createdAt,
              dateEnd = data.updatedAt,
              outputFilePath = data.path,
              outputFileSize = data.size,
              danmuFilePath = if (isDanmuEnabled) danmu.filePath else null
            )
            EventCenter.sendEvent(
              DownloadEvent.DownloadSuccess(
                filePath = data.path,
                url = downloadUrl,
                platform = streamer.platform,
                data = stream,
                time = Instant.fromEpochSeconds(data.updatedAt)
              )
            )
            onStreamDownloaded?.invoke(stream)
          }

          // normal exit
          override fun onDownloadFinished() {
            logger.debug("(${streamer.name}) download finished")
            danmuJob?.let { stopDanmuJob(it) }
          }

          // error exit
          override fun onDownloadError(filePath: String?, e: Exception) {
            logger.error("(${streamer.name}), $filePath, download failed: $e")
            EventCenter.sendEvent(
              DownloadEvent.DownloadError(
                filePath = filePath ?: outputPath.pathString,
                url = downloadUrl,
                platform = streamer.platform,
                error = e
              )
            )
            danmuJob?.let { stopDanmuJob(it) }
            logger.debug("(${streamer.name}) error danmu finished : ${danmu.filePath}")
            // process the segment
            processSegment(filePath?.let { Path(it) } ?: outputPath, Path(danmu.filePath))
            onStreamDownloadError?.invoke(e)
          }

          override fun onDownloadCancelled() {
            logger.debug("(${streamer.name}) download cancelled")

          }

          override fun onDestroy() {
            logger.debug("(${streamer.name}) download destroyed")
          }

        }
      )

      // determine if the built-in segmenter should be used
      if (this is FFmpegDownloadEngine) {
        useSegmenter = app.config.useBuiltInSegmenter
      }
    }

    withIOContext {
      try {
        engine.start()
        logger.debug("(${streamer.name}) engine finished")
      } catch (e: Exception) {
        onStreamDownloadError?.invoke(e)
        // rethrow the exception if it is an invalid download exception
        if (e is InvalidDownloadException) {
          throw e
        }
      } finally {
        pb?.close()
        engine.clean()
      }
    }
  }


  protected fun processSegment(segmentPath: Path, danmuPath: Path?): Boolean {
    val minContentLength = app.config.minPartSize
    val outputFile = Path(segmentPath.pathString)
    val fileSize = outputFile.fileSize()
    if (fileSize < minContentLength) {
      logger.error("(${streamer.name}) file size too small: $fileSize")
      deleteOutputs(outputFile, danmuPath)
      return true
    }
    return false
  }


  /**
   * Stop the download process
   * @return true if the download is stopped, false otherwise
   */
  suspend fun stopDownload(): Boolean {
    return if (::engine.isInitialized) {
      engine.stop()
    } else true
  }

  private fun deleteOutputs(outputPath: Path, danmuPath: Path? = null) {
    outputPath.deleteFile()
    danmuPath?.deleteFile()
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
    } catch (_: Exception) {
    }
  }


  private fun buildOutputFilePath(downloadConfig: DownloadConfig, fileExtension: String): Path {
    val timestamp = Clock.System.now()
    val outputFileName = (downloadConfig.outputFileName?.nonEmptyOrNull() ?: app.config.outputFileName).run {
      formatToFriendlyFileName(
        // Add PART_PREFIX to the file name to indicate that it is a part
        PART_PREFIX + replacePlaceholders(
          streamer.name,
          downloadTitle,
          timestamp,
          !app.config.useBuiltInSegmenter
        ) + ".$fileExtension"
      )
    }

    val outputFolder = (downloadConfig.outputFolder?.nonEmptyOrNull() ?: app.config.outputFolder).run {
      val str = if (endsWith(File.separator)) this else this + File.separator
      str.replacePlaceholders(streamer.name, downloadTitle, timestamp)
    }
    val sum = outputFolder + outputFileName

    return Path(sum).also {
      Files.createDirectories(it.parent)
      Files.exists(it).let { exists ->
        if (exists) {
          logger.error("(${sum}) file already exists")
          throw DownloadFilePresentException("File already exists")
        }
      }
    }
  }

  /**
   * Check if there is enough disk space
   * @param path the [Path] instance
   * @param size the minimum size required
   * @throws InsufficientDownloadSizeException if there is not enough disk space
   */
  private fun checkDiskSpace(path: Path, size: Long) {
    val fileStore = Files.getFileStore(path)
    val usableSpace = fileStore.usableSpace
    if (usableSpace < size) {
      logger.error("Not enough disk space: $usableSpace")
      throw InsufficientDownloadSizeException("Not enough disk space")
    }
  }


  /**
   * Initialize the danmu
   * @param streamer the [Streamer] instance
   * @param startTime the start time of the stream
   */
  protected suspend fun initDanmu(streamer: Streamer, startTime: Instant): Boolean = danmu.run {
    init(streamer, startTime).also {
      if (!it) {
        logger.error("(${streamer.name}) failed to initialize danmu")
      }
    }
  }

  /**
   * Stop the danmu job
   * @param danmuJob the [Job] instance
   */
  private fun stopDanmuJob(danmuJob: Job) {
    danmu.finish()
    try {
      danmuJob.cancel()
    } catch (e: Exception) {
      logger.error("(${streamer.name}) failed to cancel danmuJob: $e")
    } finally {
      danmu.clean()
    }
  }

  /**
   * Format the file name to a friendly file name
   * @param fileName the file name to be formatted
   * @return a formatted file name
   */
  private fun formatToFriendlyFileName(fileName: String): String {
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

  /**
   * Set the stream downloaded callback
   * @param callback the callback function
   */
  fun onStreamDownloaded(callback: (StreamData) -> Unit) {
    this.onStreamDownloaded = callback
  }

  /**
   * Set the stream download error callback
   * @param callback the callback function
   */
  fun onStreamDownloadError(callback: (Exception) -> Unit) {
    this.onStreamDownloadError = callback
  }
}