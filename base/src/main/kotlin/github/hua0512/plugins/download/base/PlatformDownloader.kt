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
import github.hua0512.app.COMMON_HEADERS
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.event.DownloadEvent
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.FileInfo
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.StreamInfo
import github.hua0512.data.stream.Streamer
import github.hua0512.download.exceptions.DownloadFilePresentException
import github.hua0512.download.exceptions.FatalDownloadErrorException
import github.hua0512.download.exceptions.InsufficientDownloadSizeException
import github.hua0512.flv.data.other.FlvMetadataInfo
import github.hua0512.plugins.StreamerContext
import github.hua0512.plugins.base.Extractor
import github.hua0512.plugins.base.exceptions.InvalidExtractionInitializationException
import github.hua0512.plugins.base.exceptions.InvalidExtractionUrlException
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.danmu.exceptions.DownloadProcessFinishedException
import github.hua0512.plugins.download.ProgressBarManager
import github.hua0512.plugins.download.engines.BaseDownloadEngine
import github.hua0512.plugins.download.engines.BaseDownloadEngine.Companion.PART_PREFIX
import github.hua0512.plugins.download.engines.ffmpeg.FFmpegDownloadEngine
import github.hua0512.plugins.download.engines.kotlin.KotlinFlvDownloadEngine
import github.hua0512.plugins.download.engines.kotlin.KotlinHlsDownloadEngine
import github.hua0512.plugins.event.EventCenter
import github.hua0512.utils.deleteFile
import github.hua0512.utils.formatToFileNameFriendly
import github.hua0512.utils.logger
import github.hua0512.utils.nonEmptyOrNull
import github.hua0512.utils.replacePlaceholders
import github.hua0512.utils.withIORetry
import io.ktor.http.ContentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Instant
import me.tongfei.progressbar.ProgressBar
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.pathString


typealias OnStreamDownloaded = (StreamData, FlvMetadataInfo?) -> Unit


sealed class DownloadState {
  data object Idle : DownloadState()
  data class Preparing(val downloadUrl: String, val format: VideoFormat, val title: String) : DownloadState()
  data object Downloading : DownloadState()
  data object Paused : DownloadState()
  data object Stopped : DownloadState()
  data object Finished : DownloadState()
  data class Error(val filePath: String?, val error: Exception) : DownloadState()
}


/**
 * Base class for platform downloaders
 * @author hua0512
 * @date : 2024/10/10 21:36
 */
abstract class PlatformDownloader<T : DownloadConfig>(
  val app: App,
  open val danmu: Danmu,
  open val extractor: Extractor,
) {

  companion object {
    @JvmStatic
    protected val logger: Logger = logger(this::class.java)
  }

  protected lateinit var streamer: Streamer

  protected lateinit var downloadConfig: T

  private var isInitialized = false

  private var isOneShot = false

  private var state = MutableStateFlow<DownloadState>(DownloadState.Idle)

  private var streamerCallback: StreamerCallback? = null

  private lateinit var engine: BaseDownloadEngine

  private var maxSize: Long = 0
    set(value) {
      if (value < 0) {
        throw FatalDownloadErrorException("Max download size must be greater than 0")
      }
      field = value
    }

  private var maxTime: Long = 0
    set(value) {
      if (value < 0) {
        throw FatalDownloadErrorException("Max download time must be greater than 0")
      }
      field = value
    }

  /**
   * Callback triggered when the stream is downloaded
   */
  open var onStreamDownloaded: OnStreamDownloaded? = null

  private var pb: ProgressBar? = null


  /***
   * Initialize the downloader
   * @param streamer the streamer
   * @param streamerCallback the streamer callback
   * @param maxDownloadSize the maximum download size
   * @throws IllegalArgumentException if the streamer is not supported
   */
  suspend fun init(
    streamer: Streamer,
    streamerCallback: StreamerCallback? = null,
    maxDownloadSize: Long = 0,
    maxDownloadTime: Long = 0,
  ) {
    this.streamer = streamer
    @Suppress("UNCHECKED_CAST")
    this.downloadConfig = streamer.downloadConfig as T
    extractor.prepare()
    streamerCallback?.let {
      this.streamerCallback = it
    }
    this.maxSize = maxDownloadSize
    this.maxTime = maxDownloadTime
    isInitialized = true
  }

  fun oneShotInit(downloadUrl: String, downloadFormat: VideoFormat) {
    isInitialized = true
    isOneShot = true
    state.value = DownloadState.Preparing(downloadUrl, downloadFormat, "")
  }


  /**
   * Check if download should be started
   * @param onLive the callback to be called if the stream is live
   * @return true if download should be started, false otherwise
   * @throws FatalDownloadErrorException
   * @throws InvalidExtractionInitializationException
   * @throws InvalidExtractionUrlException
   */
  open suspend fun shouldDownload(onLive: () -> Unit = {}): Boolean {
    if (!isInitialized) {
      throw FatalDownloadErrorException("Downloader is not initialized")
    }

    if (state.value == DownloadState.Downloading) {
      throw FatalDownloadErrorException("Downloader is already downloading")
      return false
    }

    if (state.value == DownloadState.Finished) {
      throw FatalDownloadErrorException("Downloader is already finished")
      return false
    }

    if (isOneShot) {
      return true
    }

    val mediaInfo = try {
      extractor.extract()
    } catch (e: Exception) {
      logger.error("Failed to extract media info", e)
      state.value = DownloadState.Error(null, e)

      if (e is InvalidExtractionInitializationException || e is InvalidExtractionUrlException) {
        throw e
      }

      return false
    }

    if (mediaInfo.live) {
      onLive()
    }

    return getStreamInfo(mediaInfo, streamer, downloadConfig)
  }


  suspend fun download() = supervisorScope {
    if (state.value == DownloadState.Downloading) {
      throw IllegalStateException("Downloader is already downloading")
      return@supervisorScope
    }

    if (state.value == DownloadState.Finished) {
      throw IllegalStateException("Downloader is already finished")
      return@supervisorScope
    }

    if (state.value == DownloadState.Idle) {
      throw IllegalStateException("Downloader is not initialized")
      return@supervisorScope
    }

    val (url, format, title) = when (val state = state.value) {
      is DownloadState.Preparing -> state.run { Triple(downloadUrl, format, title) }
      else -> throw IllegalStateException("Invalid state")
    }
    logger.debug("{}, starting download {}", streamer.name, url)


    val fileExtension = format.fileExtension
    val isDanmuEnabled = downloadConfig.danmu == true
    val genericOutputPath = buildOutputFilePath(downloadConfig, title, fileExtension)


    // check disk space
    checkDiskSpace(genericOutputPath.parent, maxSize)

    val headers = getPlatformHeaders().plus(COMMON_HEADERS)

    val kbMax = maxSize / 1024

    logger.debug("(${streamer.name}) download max size: $kbMax kb, max time: $maxTime s")

    var danmuJob: Job? = null


    val downloadCallback = object : DownloadCallback {
      override fun onInit() {
      }

      override fun onDownloadStarted(filePath: String, time: Long) {
        logger.debug("(${streamer.name}) download started: $filePath at $time")
        state.value = DownloadState.Downloading
        // init progress bar
        if (pb == null) {
          pb = ProgressBarManager.addProgressBar(url, this@PlatformDownloader.streamer.name, kbMax)
        } else if (pb!!.current != 0L) {
          pb!!.reset()
        }

        val filePath = filePath
        val time = time

        if (isDanmuEnabled) {
          danmu.videoStartTime = Instant.fromEpochSeconds(time)
          val danmuPath = filePath
            .replace(filePath.substringAfterLast("."), ContentType.Application.Xml.contentSubtype)
            .replace(PART_PREFIX, "")
          danmu.filePath = danmuPath

          if (danmuJob == null) {
            danmuJob = async {
              val status = withIORetry<Boolean>(
                maxRetries = 5,
                maxDelayMillis = 30000,
                onError = { e, count -> logger.error("(${streamer.name}) danmu failed to initialize($count): $e") }) {
                danmu.init(streamer, Instant.fromEpochSeconds(time))
              }
              if (!status) {
                logger.error("(${streamer.name}) danmu failed to initialize")
                return@async
              } else {
                logger.debug("(${streamer.name}) danmu :${danmu.filePath} initialized")
              }
              danmu.enableWrite = true
              try {
                danmu.fetchDanmu()
              } catch (_: Exception) {
                // ignore exception
              }
            }
          } else {
            // re-enable danmu write
            danmu.enableWrite = true
          }
        }

        EventCenter.sendEvent(
          DownloadEvent.DownloadStart(
            filePath = filePath,
            url = url,
            platform = streamer.platform
          )
        )
      }

      override fun onDownloadProgress(diff: Long, bitrate: Double) {
        pb?.also {
          it.stepBy(diff)
          it.extraMessage = "Bitrate: %.2f kb/s".format(bitrate)
          EventCenter.sendEvent(
            DownloadEvent.DownloadStateUpdate(
              filePath = "",
              url = url,
              platform = streamer.platform,
              duration = it.totalElapsed.toSeconds(),
              bitrate = bitrate,
              fileSize = it.current,
              streamerId = streamer.id
            )
          )
        }

      }

      override fun onDownloaded(data: FileInfo, metaInfo: FlvMetadataInfo?) {
        logger.debug("(${streamer.name}) download finished: ${data.path}, meta info: ${metaInfo != null}")
        val danmuPath = if (isDanmuEnabled) Path(danmu.filePath) else null
        onFileDownloaded(
          data, StreamData(
            title = title,
            outputFilePath = data.path,
            danmuFilePath = danmuPath?.pathString,
            streamerId = streamer.id,
            streamer = streamer,
            dateStart = data.createdAt,
            dateEnd = data.updatedAt,
            outputFileSize = data.size,
          ), danmuPath, metaInfo
        )
      }

      override fun onDownloadFinished() {
        logger.debug("(${streamer.name}) download finished")
        state.value = DownloadState.Finished
      }

      override fun onDownloadError(filePath: String?, e: Exception) {
        logger.error("(${streamer.name}) $filePath download error:", e)
        state.value = DownloadState.Error(filePath, e)
      }

      override fun onDownloadCancelled() {
        logger.error("(${streamer.name}) download cancelled")
        state.value = DownloadState.Stopped
      }

      override fun onDestroy() {
      }
    }

    val streamerContext = StreamerContext(streamer.name, title)

    engine = DownloadEngineFactory.createEngine(downloadConfig.engine!!, format).apply {
      // init engine
      init(
        url,
        format,
        genericOutputPath.pathString,
        streamerContext,
        downloadConfig.cookies,
        headers,
        fileLimitSize = maxSize,
        fileLimitDuration = maxTime,
        callback = downloadCallback
      )
      // populate program args
      val definedArgs = getProgramArgs()
      if (definedArgs.isNotEmpty()) programArgs.addAll(definedArgs)
      // configure engine
      configureEngine(app)
    }
    // start download
    try {
      engine.start()
      logger.debug("(${streamer.name}) platform download finished")
    } finally {
      ProgressBarManager.deleteProgressBar(url)
      engine.clean()
      pb = null
      if (state.value is DownloadState.Error) {
        val (filePath, error) = state.value as DownloadState.Error
        logger.error("(${streamer.name}) {} finally download error:", filePath, error)
        // clean up the outputs
        danmuJob?.let {
          danmu.finish()
          stopDanmuJob(it)
          val shouldDeleteDanmu = filePath.isNullOrEmpty() || !(Path(filePath).exists())
          if (shouldDeleteDanmu) {
            // delete the danmu file
            danmu.filePath.let { path ->
              Path(path).deleteFile()
            }
          }
          danmuJob = null
        }
        throw error
      } else if (state.value is DownloadState.Downloading) {
        // we should clean up the outputs
        // this is an abnormal termination
        logger.error("(${streamer.name}) abnormal termination")
        danmuJob?.let {
          stopDanmuJob(it)
        }
      } else if (state.value is DownloadState.Finished) {
        // clean up the outputs
        danmuJob?.let {
          stopDanmuJob(it)
        }
      }
      // we reset the state here
      state.value = DownloadState.Preparing(url, format, title)
      danmuJob = null
    }
  }


  abstract fun getPlatformHeaders(): Map<String, String>

  abstract fun getProgramArgs(): List<String>


  private fun buildOutputFilePath(config: DownloadConfig, title: String, fileExtension: String): Path {

    val configOutputFileName =
      config.outputFileName?.nonEmptyOrNull() ?: throw FatalDownloadErrorException("Output file name is null")

    val finalFileName = PART_PREFIX + (configOutputFileName.replacePlaceholders(
      streamer.name,
      title,
      replaceTimestamps = false
    ) + ".$fileExtension").formatToFileNameFriendly()

    val outputFolder =
      (config.outputFolder?.nonEmptyOrNull() ?: throw FatalDownloadErrorException("Output folder is null")).run {
        val str = if (endsWith(File.separator)) this else this + File.separator
        str.replacePlaceholders(streamer.name, title)
      }
    val sum = outputFolder + finalFileName

    return Path(sum).also {
      Files.createDirectories(it.parent)
      Files.exists(it).let { exists ->
        if (exists) {
          logger.error("(${sum}) file already exists")
          val error = DownloadFilePresentException("File already exists")
          state.value = DownloadState.Error("", error)
          throw error
        }
      }
    }
  }

  private fun onFileDownloaded(info: FileInfo, streamInfo: StreamData, danmuPath: Path?, metaInfo: FlvMetadataInfo?) {
    // check if the segment is valid
    danmuPath?.let {
      logger.debug("({}) danmu finished : {}", streamer.name, danmuPath)
      danmu.finish()
    }

    if (processSegment(Path(info.path), danmuPath)) return
    EventCenter.sendEvent(
      DownloadEvent.DownloadSuccess(
        filePath = info.path,
        url = "",
        platform = streamer.platform,
        data = streamInfo,
        time = Instant.fromEpochSeconds(info.updatedAt)
      )
    )
    onStreamDownloaded?.invoke(streamInfo, metaInfo)
  }

  private fun BaseDownloadEngine.configureEngine(app: App) {
    when (this) {
      is FFmpegDownloadEngine -> {
        // determine if the built-in segmenter should be used
        useSegmenter = app.config.useBuiltInSegmenter
        detectErrors = app.config.exitDownloadOnError
      }

      is KotlinFlvDownloadEngine -> {
        enableFlvFix = app.config.enableFlvFix
        enableFlvDuplicateTagFiltering = app.config.enableFlvDuplicateTagFiltering
      }

      is KotlinHlsDownloadEngine -> {
        combineTsFiles = app.config.combineTsFiles
      }
    }
  }


  /**
   * Process the segment.
   * This method checks if the segment is valid, if not, it deletes the outputs
   * A valid segment should exist and have a size greater than the minimum part size configured in the app config
   * Otherwise, the segment is invalid
   * @param segmentPath the path of the segment
   * @param danmuPath the path of the danmu
   * @return true if the segment is invalid, false otherwise
   */
  protected fun processSegment(segmentPath: Path, danmuPath: Path?): Boolean {
    // check if the segment is valid, a valid segment should exist and have a size greater than the minimum part size
    // m3u8 files are not considered segments
    if (segmentPath.exists() && segmentPath.extension != "m3u8" && segmentPath.fileSize() < app.config.minPartSize) {
      logger.error("(${streamer.name}) segment is invalid: ${segmentPath.pathString}")
      deleteOutputs(segmentPath, danmuPath)
      return true
    }
    return false
  }

  /**
   * Stop the download process
   * @return true if the download is stopped, false otherwise
   */
  suspend fun stopDownload(exception: Exception? = null): Boolean = if (::engine.isInitialized) {
    engine.stop(exception)
  } else true

  /**
   * Delete the outputs
   * @param outputPath the path of the output
   * @param danmuPath the path of the danmu
   */
  private fun deleteOutputs(outputPath: Path, danmuPath: Path? = null) {
    outputPath.deleteFile()
    danmuPath?.deleteFile()
  }

  /**
   * Stop the danmu job
   * @param danmuJob the [Job] instance
   */
  private suspend fun stopDanmuJob(danmuJob: Job) {
    try {
      danmuJob.cancel(CancellationException(Throwable("Cancel download", DownloadProcessFinishedException())))
      danmuJob.join()
    } catch (e: Exception) {
      if (e !is CancellationException)
        logger.error("(${streamer.name}) failed to cancel danmuJob: $e")
    } finally {
      danmu.clean()
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
      val errorMsg = "Not enough disk space: $usableSpace < $size"
      logger.error("${streamer.name} $errorMsg")
      state.value = DownloadState.Error(null, InsufficientDownloadSizeException(errorMsg))
      throw InsufficientDownloadSizeException(errorMsg)
    }
  }

  /**
   * Apply filters to the list of [StreamInfo] and return a single [StreamInfo] instance
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
      throw IllegalStateException("${streamer.name} no streams found")
      return false
    }
    val finalStreamInfo = userConfig.applyFilters(mediaInfo.streams)
    state.value = DownloadState.Preparing(finalStreamInfo.url, finalStreamInfo.format, mediaInfo.title)
    return true
  }

  /**
   * Update streamer info
   * @param mediaInfo the [MediaInfo] instance
   * @param streamer the [Streamer] instance
   */
  private suspend fun updateStreamerInfo(mediaInfo: MediaInfo, streamer: Streamer) {
    if (mediaInfo.artistImageUrl.isNotEmpty() && mediaInfo.artistImageUrl != streamer.avatar) {
      streamerCallback?.onAvatarChanged(streamer.id, mediaInfo.artistImageUrl) {
        streamer.avatar = mediaInfo.artistImageUrl
      }
    }
    if (mediaInfo.title.isNotEmpty() && mediaInfo.title != streamer.streamTitle) {
      streamerCallback?.onDescriptionChanged(streamer.id, mediaInfo.title) {
        streamer.streamTitle = mediaInfo.title
      }
    }
  }


}