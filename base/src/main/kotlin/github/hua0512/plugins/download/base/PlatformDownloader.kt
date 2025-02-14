/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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

import com.github.michaelbull.result.*
import github.hua0512.app.App
import github.hua0512.app.COMMON_HEADERS
import github.hua0512.data.config.AppConfig
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
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.danmu.base.Danmu
import github.hua0512.plugins.danmu.base.NoDanmu
import github.hua0512.plugins.danmu.exceptions.DownloadProcessFinishedException
import github.hua0512.plugins.download.ProgressBarManager
import github.hua0512.plugins.download.engines.BaseDownloadEngine
import github.hua0512.plugins.download.engines.BaseDownloadEngine.Companion.PART_PREFIX
import github.hua0512.plugins.download.engines.ffmpeg.FFmpegDownloadEngine
import github.hua0512.plugins.download.engines.kotlin.KotlinFlvDownloadEngine
import github.hua0512.plugins.download.engines.kotlin.KotlinHlsDownloadEngine
import github.hua0512.plugins.event.EventCenter
import github.hua0512.utils.*
import io.ktor.http.*
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
import kotlin.io.path.*


typealias OnStreamDownloaded = (StreamData, FlvMetadataInfo?) -> Unit


private sealed class DownloadState {
  data object Idle : DownloadState()
  data class Preparing(
    val downloadUrl: String,
    val format: VideoFormat,
    val userSelectedFormat: VideoFormat?,
    val title: String,
  ) : DownloadState()

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
) : StreamerLoggerContext {

  companion object {
    protected val logger: Logger = logger(this::class.java)
  }

  override val logger: Logger = PlatformDownloader.logger

  override lateinit var context: StreamerContext

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

  /**
   * Callback triggered when the stream is finished
   * This callback is invoked after end of danmu is detected
   */
  open var onStreamFinished: (() -> Unit)? = null

  private var pb: ProgressBar? = null


  /***
   * Initialize the downloader
   * @param streamer the streamer
   * @param streamerCallback the streamer callback
   * @param maxDownloadSize the maximum download size
   * @throws IllegalArgumentException if the streamer is not supported
   */
  fun init(
    streamer: Streamer,
    streamerCallback: StreamerCallback? = null,
    maxDownloadSize: Long = 0,
    maxDownloadTime: Long = 0,
  ): Result<Boolean, ExtractorError> {
    this.streamer = streamer
    this.context = StreamerContext(streamer.name, streamer.streamTitle.orEmpty(), streamer.platform.name)
    @Suppress("UNCHECKED_CAST")
    this.downloadConfig = streamer.downloadConfig as T
    val initialization = extractor.prepare()
    if (initialization.isErr) {
      return initialization.asErr()
    }
    streamerCallback?.let {
      this.streamerCallback = it
    }
    this.maxSize = maxDownloadSize
    this.maxTime = maxDownloadTime
    isInitialized = true
    return Ok(true)
  }

  fun oneShotInit(downloadUrl: String, downloadFormat: VideoFormat) {
    isInitialized = true
    isOneShot = true
    state.value = DownloadState.Preparing(downloadUrl, downloadFormat, downloadFormat, "")
  }


  /**
   * Check if download should be started
   * @param onLive the callback to be called if the stream is live
   * @return true if download should be started, false otherwise
   */
  open suspend fun shouldDownload(onLive: () -> Unit = {}): Result<Boolean, ExtractorError> {
    if (!isInitialized) {
      return Err(ExtractorError.InitializationError(FatalDownloadErrorException("Downloader is not initialized")))
    }

    if (state.value == DownloadState.Downloading) {
      return Err(ExtractorError.InitializationError(FatalDownloadErrorException("Downloader is already downloading")))
    } else if (state.value == DownloadState.Finished) {
      return Err(ExtractorError.InitializationError(FatalDownloadErrorException("Downloader is already finished")))
    }

    if (isOneShot) {
      return Ok(true)
    }

    // set cookies
    extractor.cookies = streamer.downloadConfig?.cookies.orEmpty()

    val extractorResult = try {
      extractor.extract()
    } catch (e: Exception) {
      error("unexpected extraction error:", throwable = e)
//      state.value = DownloadState.Error(null, e)
      return Ok(false)
    }

    if (extractorResult.isErr) {
      val result = extractorResult.analyzeError()
      // propagate the error
      if (result.isErr || !result.value) return result
      // there is no possible `true` value if error occurred
      return Ok(false)
    }

    // result is ok and live
    val mediaInfo = extractorResult.unwrap()
    if (mediaInfo.live) {
      onLive()
    } else {
      return Ok(false)
    }

    var filterResult = getStreamInfo(mediaInfo, streamer)

    if (filterResult.isErr) {
      filterResult = filterResult.analyzeError()
      if (filterResult.isErr) return filterResult
      if (!filterResult.value) {
        return Ok(false)
      }
    }
    // success
    return filterResult
  }

  private fun <T> Result<T, ExtractorError>.analyzeError(): Result<Boolean, ExtractorError> {
    // ensure is error
    return when (val error = this.unwrapError()) {
      ExtractorError.InvalidExtractionUrl -> {
        error("Invalid extraction url")
        return this.asErr()
      }

      is ExtractorError.InitializationError -> {
        error("Initialization error: {}", error.throwable)
        return this.asErr()
      }

      ExtractorError.StreamerBanned -> this.asErr()
      ExtractorError.StreamerNotFound -> this.asErr()

      is ExtractorError.ApiError -> {
        error("Api error: {}", error.throwable)
        Ok(false)
      }

      is ExtractorError.FallbackError -> Ok(false)
      is ExtractorError.InvalidResponse -> {
        error("Invalid response: {}", error.message)
        Ok(false)
      }

      is ExtractorError.JsEngineError -> {
        error("Js engine error: {}", error.throwable)
        Ok(false)
      }

      ExtractorError.NoStreamsFound -> {
        error("No matching streams found")
        Ok(false)
      }
    }
  }


  suspend fun download() = supervisorScope {
    require(isInitialized) { "Downloader is not initialized" }
    if (state.value == DownloadState.Downloading) {
      throw IllegalStateException("Downloader is already downloading")
    }

    if (state.value == DownloadState.Finished) {
      throw IllegalStateException("Downloader is already finished")
    }

    if (state.value == DownloadState.Idle) {
      throw IllegalStateException("Downloader is not initialized")
    }

    require(state.value is DownloadState.Preparing) { "${streamer.name} Invalid state" }

    val (url, format, userSelectedFormat, title) = state.value as DownloadState.Preparing

    // update context title
    this@PlatformDownloader.context = this@PlatformDownloader.context.copy(title = title)

    debug("starting download {}", url)


    val fileExtension = format.fileExtension
    val isDanmuEnabled = downloadConfig.danmu == true && danmu !is NoDanmu

    val genericOutputPath =
      buildOutputFilePath(downloadConfig, title, userSelectedFormat?.fileExtension ?: fileExtension)

    val isSpaceAvailable = checkSpaceAvailable(Path(genericOutputPath.pathString.substringBeforePlaceholders().let {
      if (it.endsWith(File.separator)) it else it + File.separator
    }))

    if (!isSpaceAvailable) {
      error("not enough disk space")
      state.value = DownloadState.Error("", InsufficientDownloadSizeException("Not enough disk space"))
      throw InsufficientDownloadSizeException("Not enough disk space")
    }

    val headers = buildMap {
      putAll(COMMON_HEADERS)
      putAll(getPlatformHeaders())
    }

    val kbMax = maxSize / 1024

    debug("download max size: {} kb, max time: {} s", kbMax, maxTime)

    var danmuJob: Job? = null

    var hasEndOfDanmu = false

    val downloadCallback = object : DownloadCallback {
      override fun onInit() {
      }

      override fun onDownloadStarted(filePath: String, time: Long) {
        debug("download started: {} at {}", filePath, time)
        state.value = DownloadState.Downloading
        // init progress bar
        if (pb == null) {
          pb = ProgressBarManager.addProgressBar(url, this@PlatformDownloader.streamer.name, kbMax)
        } else if (pb!!.current != 0L) {
          pb!!.reset()
        }

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
                onError = { e, count -> error("danmu failed to initialize ({}):", count, throwable = e) }) {
                danmu.init(streamer, Instant.fromEpochSeconds(time))
              }
              if (!status) {
                error("danmu failed to initialize")
                return@async
              } else {
                debug("danmu: {} initialized", danmu.filePath)
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
        debug("download finished: {}, meta info: {}", data.path, metaInfo != null)
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
        debug("download finished")
        state.value = DownloadState.Finished
      }

      override fun onDownloadError(filePath: String?, e: Exception) {
//        error("{} download error:", filePath, throwable = e)
        state.value = DownloadState.Error(filePath, e)
      }

      override fun onDownloadCancelled() {
        error("download cancelled")
        state.value = DownloadState.Stopped
      }

      override fun onDestroy() {
      }
    }

    engine = DownloadEngineFactory.createEngine(downloadConfig.engine!!, format).apply {
      // init engine
      init(
        url,
        userSelectedFormat ?: format,
        genericOutputPath.pathString,
        this@PlatformDownloader.context,
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
      configureEngine(app.config)
      // listen for end of danmu event
      danmu.setOnDanmuClosedCallback { hasEndOfDanmu = true }
    }
    // start download
    try {
      engine.start()
      debug("platform download finished")
    } finally {
      ProgressBarManager.deleteProgressBar(url)
      engine.clean()
      pb = null

      when (state.value) {
        is DownloadState.Error -> {
          val (filePath, error) = state.value as DownloadState.Error

          // call onStreamFinished if danmu is enabled and end of danmu is detected
          if (isDanmuEnabled && hasEndOfDanmu) {
            info("end of stream detected")
            onStreamFinished?.invoke()
          } else {
            error("{} finally download error: {}", filePath, error.message)
            onDownloadError(error)
          }

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
          if (!hasEndOfDanmu) throw error
        }

        is DownloadState.Downloading -> {
          // we should clean up the outputs
          // this is an abnormal termination
          error("abnormal termination")
          danmuJob?.let {
            stopDanmuJob(it)
          }
        }

        is DownloadState.Finished -> {
          // clean up the outputs
          danmuJob?.let {
            stopDanmuJob(it)
          }
        }

        else -> {}
      }
      // we reset the state here
      state.value = DownloadState.Preparing(url, format, userSelectedFormat, title)
      danmuJob = null
    }
  }


  open fun getPlatformHeaders(): Map<String, String> = extractor.getRequestHeaders()

  abstract fun getProgramArgs(): List<String>

  open fun onConfigUpdated(config: AppConfig) {
    this@PlatformDownloader.maxSize = config.maxPartSize
    this@PlatformDownloader.maxTime = config.maxPartDuration ?: 0
    if (this::engine.isInitialized) {
      engine.configureEngine(config)
    }
  }

  protected open fun onDownloadError(exception: Exception) {

  }

  private fun buildOutputFilePath(config: DownloadConfig, title: String, fileExtension: String): Path {

    val configOutputFileName =
      config.outputFileName?.nonEmptyOrNull() ?: throw FatalDownloadErrorException("Output file name is null")

    val finalFileName = PART_PREFIX + (configOutputFileName.replacePlaceholders(
      streamer.name,
      title
    ) + ".$fileExtension").formatToFileNameFriendly()

    val outputFolder =
      (config.outputFolder?.nonEmptyOrNull() ?: throw FatalDownloadErrorException("Output folder is null")).run {
        val str = if (endsWith(File.separator)) this else this + File.separator
        str.replacePlaceholders(streamer.name, title)
      }
    val sum = outputFolder + finalFileName

    return Path(sum).also {
      Files.exists(it).let { exists ->
        if (exists) {
          error("{} file already exists", sum)
          val error = DownloadFilePresentException("$sum file already exists")
          state.value = DownloadState.Error("", error)
          throw error
        }
      }
    }
  }

  private fun onFileDownloaded(info: FileInfo, streamInfo: StreamData, danmuPath: Path?, metaInfo: FlvMetadataInfo?) {
    // check if the segment is valid
    danmuPath?.let {
      debug("danmu finished : {}", danmuPath)
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

  private fun BaseDownloadEngine.configureEngine(config: AppConfig) {
    when (this) {
      is FFmpegDownloadEngine -> {
        // determine if the built-in segmenter should be used
        useSegmenter = config.useBuiltInSegmenter
        detectErrors = config.exitDownloadOnError
      }

      is KotlinFlvDownloadEngine -> {
        enableFlvFix = config.enableFlvFix
        enableFlvDuplicateTagFiltering = config.enableFlvDuplicateTagFiltering
      }

      is KotlinHlsDownloadEngine -> {
        combineTsFiles = config.combineTsFiles
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
  private fun processSegment(segmentPath: Path, danmuPath: Path?): Boolean {
    // check if the segment is valid, a valid segment should exist and have a size greater than the minimum part size
    // m3u8 files are not considered segments
    if (segmentPath.exists() && segmentPath.extension != VideoFormat.hls.fileExtension && segmentPath.fileSize() < app.config.minPartSize) {
      error("segment is invalid: {}", segmentPath.pathString)
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
      danmuJob.cancel(DownloadProcessFinishedException())
      danmuJob.join()
    } catch (e: Exception) {
      if (e !is CancellationException)
        error("failed to cancel danmuJob: $e")
    } finally {
      danmu.clean()
    }
  }


  open fun checkSpaceAvailable(
    genericOutputPath: Path = Path(app.config.outputFolder.substringBeforePlaceholders().let {
      if (it.endsWith(File.separator)) it else it + File.separator
    }),
  ): Boolean {
    if (maxSize == 0L) return true
    // use app config output path folder if the current output folder is child of the app config output folder
    var checkPath = genericOutputPath
    val appOutputPath = Path(app.config.outputFolder.substringBeforePlaceholders().let {
      if (it.endsWith(File.separator)) it else it + File.separator
    })
    if (checkPath != appOutputPath && checkPath.startsWith(appOutputPath)) {
      checkPath = appOutputPath
    }
    // check disk space
    return checkDiskSpace(checkPath, maxSize)
  }

  /**
   * Check if there is enough disk space
   * @param path the [Path] instance
   * @param size the minimum size required
   * @throws InsufficientDownloadSizeException if there is not enough disk space
   */
  private fun checkDiskSpace(path: Path, size: Long): Boolean {
    if (size == 0L) return true
    // path should be a dir
    var path = path
    if (!Files.isDirectory(path)) {
      path = path.parent
    }
    val fileStore = Files.getFileStore(path)
    val usableSpace = fileStore.usableSpace
    logger.debug("checking available disk space of path {}, usable space: {} bytes", path, usableSpace)
    if (usableSpace < size) {
      val errorMsg = "Not enough disk space: $usableSpace < $size"
      error(errorMsg)
      return false
    }
    return true
  }

  /**
   * Apply filters to the list of [StreamInfo] and return a single [StreamInfo] instance
   * @param streams the list of [StreamInfo] to be filtered
   * @return a [StreamInfo] instance
   */
  abstract suspend fun applyFilters(streams: List<StreamInfo>): Result<StreamInfo, ExtractorError>

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
  ): Result<Boolean, ExtractorError> {
    updateStreamerInfo(mediaInfo, streamer)
    if (!mediaInfo.live) return Ok(false)
    if (mediaInfo.streams.isEmpty()) {
      return Err(ExtractorError.NoStreamsFound)
    }
    val filterResult = applyFilters(mediaInfo.streams)

    if (filterResult.isErr) return filterResult.asErr()

    // get true URL
    val streamInfoResult = extractor.getTrueUrl(filterResult.value)

    if (streamInfoResult.isErr) {
      return streamInfoResult.asErr()
    }

    val streamInfo = streamInfoResult.value

    state.value =
      DownloadState.Preparing(streamInfo.url, streamInfo.format, downloadConfig.outputFileFormat, mediaInfo.title)
    return Ok(true)
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