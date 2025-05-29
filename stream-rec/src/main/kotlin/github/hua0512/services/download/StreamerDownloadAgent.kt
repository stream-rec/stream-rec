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

package github.hua0512.services.download

import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.config.engine.EngineConfig
import github.hua0512.data.event.StreamerEvent
import github.hua0512.data.event.StreamerEvent.StreamerException
import github.hua0512.data.event.StreamerEvent.StreamerOffline
import github.hua0512.data.event.StreamerEvent.StreamerOnline
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamerState
import github.hua0512.download.exceptions.*
import github.hua0512.flv.data.other.FlvMetadataInfo
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.plugins.download.base.StreamerCallback
import github.hua0512.plugins.download.globalConfig
import github.hua0512.plugins.event.EventCenter
import github.hua0512.utils.RateLimiter
import github.hua0512.utils.logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.collections.toList
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

sealed class AgentState {
  data object Idle : AgentState()
  data object Initializing : AgentState()
  data class Scheduled(val triggerTimeEpochMillis: Long) : AgentState()
  data object Polling : AgentState()
  data object CheckingLive : AgentState()
  data class Downloading(val currentStreamData: StreamData, val sessionStartTimeEpochSeconds: Long) : AgentState()
  data class Retrying(val attempt: Int, val maxRetries: Int, val lastError: Throwable?) : AgentState()
  data object Finishing : AgentState()
  data object Cancelled : AgentState()
  data class FatalError(val error: Throwable) : AgentState()
  data object Done : AgentState()
}


class StreamerDownloadAgent(
  private val app: App,
  private var streamer: Streamer,
  private val plugin: PlatformDownloader<out DownloadConfig>,
  private val callback: StreamerCallback,
  private val downloadSemaphore: Semaphore,
  private val platformRateLimiter: RateLimiter?,
  private val agentScope: CoroutineScope,
) {

  companion object {
    @JvmStatic
    private val logger: Logger = logger(StreamerDownloadAgent::class.java)

    /**
     * Error threshold to check with the last error time, if the difference is less than this value
     * then it means the error is recent, and the download failed consecutively. In this case, delay the download
     * for a longer time.
     */
    private const val MIN_ERROR_THRESHOLD_MS = 5000L

    /**
     * Maximum delay to wait before retrying the download if recent errors occurred
     */
    private const val MAX_ERROR_DELAY_MS = 3_600_000L

    /**
     * Error threshold to check if the recent errors count is greater than this value
     */
    private const val ERROR_THRESHOLD_COUNT = 3
  }

  /**
   * Flag to indicate if the plugin has been initialized successfully.
   */
  private var isPluginInitialized = atomic(false)

  /**
   * List of segments that have been downloaded during the current session.
   */
  private val downloadedSegments = mutableListOf<StreamData>()

  /**
   * Internal counter for retry attempts.
   */
  private var currentRetryCountInternal = 0

  /**
   * Maximum number of retries for downloading a stream.
   */
  private val maxRetries: Int get() = app.config.maxDownloadRetries

  private val streamCheckInterval: Duration
    get() = (streamer.platform.globalConfig(app.config).downloadCheckInterval
      ?: app.config.downloadCheckInterval).toDuration(DurationUnit.SECONDS)

  private val postStreamRetryDelay: Duration
    get() = app.config.downloadRetryDelay.toDuration(DurationUnit.SECONDS)

  private val partedDownloadRetryDelay: Duration
    get() = (streamer.platform.globalConfig(app.config).partedDownloadRetry ?: 0).toDuration(DurationUnit.SECONDS)

  private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
  val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

  private val isCanceledInternally = MutableStateFlow(false)

  /**
   * Flag to indicate if this agent is configured to handle global engine config changes.
   */
  var isGlobalEngineConfig = false

  /**
   * Atomic flag to indicate if a download is currently active.
   */
  private var isDownloadingActive = atomic(false)


  /**
   * Timer job to await before downloading
   */
  private var scheduleTimerJob: Job? = null


  /**
   * Timer job to stop the download after the timer ends
   */
  private var durationStopJob: Job? = null


  /**
   * Last error time
   */
  private var lastErrorTimeEpochMs: Long = 0

  /**
   * Recent errors count
   */
  private var recentErrorCount = 0

  /**
   * Recent error delay
   */
  private var currentErrorBackoffDelayMs = MIN_ERROR_THRESHOLD_MS

  suspend fun start() {
    logger.info("${streamer.name}: Agent starting...")

    agentScope.launch {
      isCanceledInternally.collect { cancelled ->
        if (cancelled && _agentState.value !is AgentState.Cancelled && _agentState.value !is AgentState.Done && _agentState.value !is AgentState.FatalError) {
          logger.info("${streamer.name}: Cancellation received by internal collector, stopping plugin if active...")
          if (isDownloadingActive.value) {
            plugin.stopDownload(UserStoppedDownloadException("Agent cancelled by orchestrator during download"))
          }
          _agentState to AgentState.Cancelled
          cleanupJobs()
        }
      }
    }


    // there might be a case of abnormal termination, so we reset the state to not live
    // reset the streamer state to not live
    updateStreamerState(StreamerState.NOT_LIVE, "Agent started, resetting state to NOT_LIVE")

    if (!initializePlugin()) {
      // FatalError state already set by initializePlugin
      return
    }
    mainLoop()
  }


  private suspend fun mainLoop() {
    while (agentScope.isActive && !isCanceledInternally.value) {
      val currentState = _agentState.value
      logger.trace("{}: Agent loop, current state: {}", streamer.name, currentState)
      when (currentState) {
        AgentState.Idle -> {
          _agentState to AgentState.Polling
        }

        AgentState.Initializing -> {
          // This state is typically brief, handled by initializePlugin. If stuck, it's an issue.
          // If initializePlugin was successful, it should have transitioned.
          // If it failed, it's FatalError. If somehow Idle, re-poll.
          if (isPluginInitialized.value) _agentState to AgentState.Polling else initializePlugin()
        }

        is AgentState.Scheduled -> {
          handleScheduledState(currentState)
        }

        AgentState.Polling -> {
          handlePollingState()
        }

        AgentState.CheckingLive -> {
          handleCheckingLiveState()
        }

        is AgentState.Downloading -> {
          handleDownloadingState()
        }

        is AgentState.Retrying -> {
          handleRetryingState(currentState)
        }

        AgentState.Finishing -> {
          handleFinishingState()
        }

        AgentState.Cancelled -> {
          logger.info("${streamer.name}: Agent processing cancelled state reached.")
          cleanupJobs()
          break
        }

        is AgentState.FatalError -> {
          logger.error(
            "${streamer.name}: Agent in fatal error state: ${currentState.error.message}",
            currentState.error
          )
          cleanupJobs()
          // Callback for fatal error already made when transitioning to this state
          break
        }

        AgentState.Done -> {
          logger.info("${streamer.name}: Agent has completed its session. Transitioning to Polling.")
          _agentState to AgentState.Polling
          delay(streamCheckInterval)
        }
      }
      if (agentScope.isActive && _agentState.value !is AgentState.FatalError && _agentState.value != AgentState.Cancelled) {
        yield()
      }
    }
    logger.info("${streamer.name}: Agent main loop ended. Final state: ${_agentState.value}")
    if (_agentState.value != AgentState.Cancelled && _agentState.value !is AgentState.FatalError) {
      _agentState to AgentState.Idle
    }
  }

  private suspend fun handleScheduledState(currentState: AgentState.Scheduled) {
    logger.info("${streamer.name}: Scheduled for ${currentState.triggerTimeEpochMillis}. Waiting...")
    val delayMillis = currentState.triggerTimeEpochMillis - Clock.System.now().toEpochMilliseconds()
    if (delayMillis > 0) {
      try {
        delay(delayMillis)
      } catch (e: CancellationException) {
        logger.info("${streamer.name}: Schedule delay cancelled.")
        if (isCanceledInternally.value) _agentState to AgentState.Cancelled
        return // Exit handling if cancelled
      }
    }
    if (isCanceledInternally.value) return
    _agentState to AgentState.Polling
  }

  private suspend fun handlePollingState() {
    if (!isInScheduledTimeRange()) {
      updateStreamerState(
        StreamerState.OUT_OF_SCHEDULE,
        "Streamer is not in scheduled time range, will poll"
      )
      val nextScheduleTime = getNextScheduledStartTimeEpochMillis()
      if (nextScheduleTime != null) {
        _agentState to AgentState.Scheduled(nextScheduleTime)
        return
      } else {
        logger.debug("${streamer.name}: Not in scheduled time range and no upcoming specific schedule. Will poll with interval.")
        // Fall through to delay if always_check or no specific schedule implies regular polling
      }
    }
    _agentState to AgentState.CheckingLive
  }

  private suspend fun handleCheckingLiveState() {
    // Acquire platform rate limiter before checking live status
    platformRateLimiter?.acquire()

//    logger.debug("${streamer.name}: Checking live status...")

    val liveStatusResult = plugin.shouldDownload()

    // Check if the agent was canceled during the live status check
    if (isCanceledInternally.value) return

    if (liveStatusResult.isOk) {
      if (liveStatusResult.value) {
        logger.info("${streamer.name} is LIVE")
        resetErrorTracking()
        // Reset internal retry counter for new live session
        currentRetryCountInternal = 0
        val now = Clock.System.now()

        // TODO: just a placeholder, we need to replace this for a SessionData, or just refactor the StreamData
        val newStreamData = StreamData(
          streamerId = streamer.id,
          title = streamer.streamTitle ?: streamer.name,
          id = 0,
          dateStart = now.epochSeconds,
          dateEnd = null,
          outputFilePath = "",
          danmuFilePath = "",
          outputFileSize = 0,
          streamer = streamer
        )
        _agentState to AgentState.Downloading(newStreamData, Clock.System.now().epochSeconds)
      } else {
        // Streamer is not live
        logger.debug("${streamer.name} is OFFLINE")
        _agentState to AgentState.Polling
        // Transition to Polling state after a standard streamCheckInterval delay
        delay(streamCheckInterval)
      }
    } else {
      val error = liveStatusResult.error
      logger.warn("${streamer.name}: Error checking live status: $error")
      handleStreamCheckError(error)
    }
  }

  private suspend fun handleDownloadingState() {
    var downloadCycleSuccess = false
    try {
      downloadSemaphore.withPermit {
        updateStreamerState(
          StreamerState.LIVE,
          "Stream is live, starting download"
        )
        updateLastLiveTime()

        // check if the agent was canceled
        if (isCanceledInternally.value) return@withPermit
        isDownloadingActive.value = true
        logger.debug("${streamer.name}: Acquired download permit. Starting download process...")

        val durationMillis = calculateDownloadDurationMillis()
        if (durationMillis > 0) {
          durationStopJob?.cancel()
          durationStopJob = agentScope.launch {
            delay(durationMillis)
            if (isActive && isDownloadingActive.value) { // ensure job is still relevant
              logger.info("${streamer.name}: Download duration ended. Stopping plugin.")
              plugin.stopDownload(TimerEndedDownloadException())
            }
          }
        }
        downloadCycleSuccess = performDownloadCycle()
      }
    } catch (e: Exception) {
      // Catch exceptions from withPermit or if performDownloadCycle rethrows something unexpected
      logger.error("${streamer.name}: Exception during semaphore permit block or unhandled in download cycle: $e", e)
      if (!isCanceledInternally.value) {
        _agentState.value = AgentState.Retrying(currentRetryCountInternal + 1, maxRetries, e)
      }
    } finally {
      isDownloadingActive.value = false
      // Ensure duration job is cancelled on exit
      durationStopJob?.cancel()
    }

    if (isCanceledInternally.value) {
      _agentState.value = AgentState.Cancelled
      return
    }

    // If performDownloadCycle itself changed state (e.g. to Finishing, Retrying), that state will be picked up in next loop iteration.
    // If downloadCycleSuccess is true (stream ended naturally), performDownloadCycle should have set state to Finishing.
    // If downloadCycleSuccess is false (part ended, or error handled by setting Retrying state), the new state is already set.
    // If it fell through without state change and no error, it implies a part completed and we should re-check.
    if (_agentState.value is AgentState.Downloading && !downloadCycleSuccess) {
      // This case implies a part might have finished and performDownloadCycle didn't set a new state.
      // This shouldn't happen if performDownloadCycle is robust. Default to re-checking.
      logger.debug("${streamer.name}: Download cycle ended, no explicit state change by cycle. Re-checking live status.")
      _agentState.value = AgentState.CheckingLive
    }
  }

  private suspend fun handleRetryingState(currentState: AgentState.Retrying) {
    // Sync internal counter
    currentRetryCountInternal = currentState.attempt
    if (currentRetryCountInternal >= maxRetries) {
      logger.error("${streamer.name}: Max retries (${maxRetries}) reached. Last error: ${currentState.lastError?.message}")
      val error = currentState.lastError ?: RuntimeException("Max retries reached without error")
      _agentState.value = AgentState.FatalError(error)

      updateStreamerState(
        StreamerState.FATAL_ERROR,
        error.message ?: "Max retries reached"
      )
    } else {
      if (streamer.state == StreamerState.LIVE)
        updateStreamerState(StreamerState.INSPECTING_LIVE)

      val delayMs = getRetryDelayMs(currentState.lastError)
      logger.info("${streamer.name}: Retrying download (attempt ${currentRetryCountInternal + 1}/$maxRetries) after ${delayMs}ms. Error: ${currentState.lastError?.message}")
      try {
        delay(delayMs)
      } catch (e: CancellationException) {
        logger.info("${streamer.name}: Retry delay cancelled.")
        if (isCanceledInternally.value) _agentState.value = AgentState.Cancelled
        return
      }
      if (isCanceledInternally.value) return
      _agentState.value = AgentState.Polling
    }
  }

  private suspend fun handleFinishingState() {
    if (downloadedSegments.isNotEmpty()) {
      logger.info("${streamer.name}: Stream finished. Finalizing ${downloadedSegments.size} segments.")
      callback.onStreamFinished(
        streamer.id, streamer.url, downloadedSegments.toList()
      )
      downloadedSegments.clear()
    }
    _agentState.value = AgentState.Done

    updateStreamerState(
      StreamerState.NOT_LIVE,
      "Stream finished"
    )

    resetErrorTracking()
    currentRetryCountInternal = 0
  }

  private suspend fun initializePlugin(): Boolean {
    _agentState.value = AgentState.Initializing
    logger.debug("${streamer.name}: Initializing plugin...")
    val result = plugin.init(
      streamer,
      callback,
      streamer.engine!!,
      streamer.engineConfig!!,
      app.config.maxPartSize,
      app.config.maxPartDuration ?: 0
    )
    if (isCanceledInternally.value) {
      _agentState.value = AgentState.Cancelled
      return false
    }
    return if (result.isOk) {
      isPluginInitialized.value = true
      logger.info("${streamer.name}: Plugin initialized successfully.")
      _agentState.value = AgentState.Polling
      true
    } else {
      val error = result.error
      logger.error("${streamer.name}: Plugin initialization failed: $error", error)
      val throwable = Throwable("Plugin initialization failed: $error")
      _agentState.value = AgentState.FatalError(throwable)

      EventCenter.sendEvent(
        StreamerException(
          streamer.name,
          streamer.url,
          streamer.platform,
          Clock.System.now(),
          IllegalStateException("Plugin initialization error: $error")
        )
      )

      updateStreamerState(
        StreamerState.FATAL_ERROR,
        throwable.message ?: "Plugin initialization failed"
      )
      false
    }
  }

  private suspend fun performDownloadCycle(): Boolean {
    var streamEndedNaturallyByPlugin = false
    try {
      plugin.onStreamDownloaded = onStreamDownloaded@{ data, metaInfo ->
//        if (isCancelledInternally.value) return@onStreamDownloaded
        onSegmentDownloaded(data, metaInfo)
      }
      plugin.onStreamFinished = onStreamFinished@{
//        if (isCancelledInternally.value) return@onStreamFinished
        logger.info("${streamer.name}: Plugin reported stream finished.")
        streamEndedNaturallyByPlugin = true
      }

      // blocking download
      plugin.download()

      // Check immediately after download() returns
      if (isCanceledInternally.value) {
        _agentState.value = AgentState.Cancelled
        return false
      }

      if (streamEndedNaturallyByPlugin) {
        _agentState.value = AgentState.Finishing
        return true
      } else {
        logger.info("${streamer.name}: Download part finished or plugin returned without explicit end. Re-checking live status after platform delay.")
        delay(partedDownloadRetryDelay)
        if (isCanceledInternally.value) return false
        _agentState.value = AgentState.CheckingLive
        return false
      }

    } catch (e: Exception) {
      if (isCanceledInternally.value && e !is CancellationException) {
        logger.warn("${streamer.name}: Download cycle caught exception ($e) but agent is already cancelling.")
        _agentState.value = AgentState.Cancelled
        return false
      }
      // Handle specific exceptions or transition to Retrying
      logger.warn(
        "${streamer.name}: Exception during download cycle: ${e.message}",
        if (e !is ExpectedException) e else null
      )

      when (e) {
        is InsufficientDownloadSizeException -> {
          logger.error("{} insufficient download space, hanging...", streamer.name)

          updateStreamerState(
            StreamerState.NO_SPACE, "Insufficient download space, waiting for space to become available"
          )

          while (true) {
            delay(30.toDuration(DurationUnit.SECONDS))
            if (isCanceledInternally.value) {
              break
            }
            if (plugin.checkSpaceAvailable()) {
              logger.info("{} space available, resuming download", streamer.name)
              break
            }
          }
          _agentState.value = AgentState.Retrying(currentRetryCountInternal + 1, maxRetries, e)
        }

        is FatalDownloadErrorException -> {
          updateStreamerState(
            StreamerState.FATAL_ERROR,
            "Fatal download error: ${e.message}"
          )
          _agentState.value = AgentState.FatalError(e)
        }

        is UserStoppedDownloadException, is TimerEndedDownloadException -> {
          // These are expected ways for download to stop not due to external cancellation signal
          // If segments exist, finish them. Otherwise, re-poll.
          logger.info("${streamer.name}: Download stopped by plugin: ${e.javaClass.simpleName}")
          if (downloadedSegments.isNotEmpty()) {
            _agentState.value = AgentState.Finishing
          } else {
            _agentState.value = AgentState.Polling
          }
        }

        is CancellationException -> {
          logger.info("${streamer.name}: Download cycle cancelled by coroutine cancellation.")
          _agentState.value = AgentState.Cancelled
        }

        else -> {
          onSegmentFailed(e)
          _agentState.value = AgentState.Retrying(currentRetryCountInternal + 1, maxRetries, e)
        }
      }
      return false
    }
  }

  private fun onSegmentDownloaded(data: StreamData, metaInfo: FlvMetadataInfo? = null) {
    logger.debug("${streamer.name}: Segment downloaded: ${data.outputFilePath}")
    downloadedSegments.add(data)
    callback.onStreamDownloaded(streamer.id, streamer.url, data, metaInfo != null, metaInfo)
  }

  private fun onSegmentFailed(error: Throwable) {
    val nextRetry = currentRetryCountInternal + 1
    logger.error(
      "{} unable to get stream data ({}/{}) due to: ",
      streamer.name,
      nextRetry,
      maxRetries,
      error
    )
    updateErrorTracking()
  }


  fun stop() {
    logger.info("${streamer.name}: Agent stop requested by orchestrator.")
    isCanceledInternally.value = true
    // The collector for isCancelledInternally should handle actual plugin stop and state transition.
  }

  suspend fun stopBlocking() {
    logger.info("${streamer.name}: Agent stop requested by orchestrator (blocking).")
    isCanceledInternally.value = true
    // Wait for the agent to finish processing cancellation

    withTimeoutOrNull(10000) {
      while (_agentState.value !is AgentState.Cancelled && _agentState.value !is AgentState.Done && _agentState.value !is AgentState.FatalError) {
        delay(100)
      }
    } ?: run {
      logger.warn("${streamer.name}: Agent stop timed out after 10 seconds, forcing cancellation.")
      // stop the plugin if still active
      if (isDownloadingActive.value) {
        plugin.stopDownload(UserStoppedDownloadException("Agent stop timed out"))
      }
      // Force cancellation state
      _agentState.value = AgentState.Cancelled
    }

    logger.info("${streamer.name}: Agent stop completed.")
  }


//  fun updateStreamerConfig(newStreamer: Streamer) {
//    logger.info("${streamer.name}: Configuration updated by orchestrator.")
//    val oldStreamer = this.streamer
//    this.streamer = newStreamer
//
//    // If schedule changed, might need to interrupt delays or re-evaluate.
//    if (oldStreamer.startTime != newStreamer.startTime || oldStreamer.endTime != newStreamer.endTime) {
//      if (_agentState.value is AgentState.Scheduled || _agentState.value == AgentState.Polling) {
//        scheduleTimerJob?.cancel() // Cancel existing schedule wait
//        _agentState.value = AgentState.Polling // Force re-evaluation
//      }
//    }
//    // Other config changes (e.g. engine, download path) are handled by plugin via updateAppConfig or re-init if major.
//    // For simplicity, plugin re-initialization on minor config changes is avoided unless critical.
//    // The plugin itself should handle dynamic updates if possible via its onConfigUpdated.
//  }

  fun updateAppConfig(newAppConfig: AppConfig) {
    logger.info("${streamer.name}: App config updated by orchestrator.")
    plugin.onConfigUpdated(newAppConfig)
  }

  private fun isInScheduledTimeRange(): Boolean {
    val definedStartTime = streamer.startTime ?: ""
    val definedStopTime = streamer.endTime ?: ""
    if (definedStartTime.isEmpty() && definedStopTime.isEmpty()) return true
    if (definedStartTime.isEmpty() != definedStopTime.isEmpty()) {
      logger.warn("${streamer.name}: Partial schedule defined (one of start/end time is empty). Treating as unscheduled.")
      return true
    }

    val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    val jStartTime = definedStartTime.toJavaLocalDateTimeFromHM(currentTime)
    val jEndTime = definedStopTime.toJavaLocalDateTimeFromHM(currentTime)

    return if (jStartTime.isAfter(jEndTime)) {
      currentTime.isAfter(jStartTime) || currentTime.isBefore(jEndTime)
    } else {
      currentTime.isAfter(jStartTime) && currentTime.isBefore(jEndTime)
    }
  }

  private fun getNextScheduledStartTimeEpochMillis(): Long? {
    val definedStartTime = streamer.startTime ?: ""
    if (definedStartTime.isEmpty()) return null

    val currentLocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    var jStartTime = definedStartTime.toJavaLocalDateTimeFromHM(currentLocalDateTime)

    if (jStartTime.isBefore(currentLocalDateTime) || jStartTime == currentLocalDateTime) {
      jStartTime = definedStartTime.toJavaLocalDateTimeFromHM(currentLocalDateTime.plusDays(1))
    }
    return jStartTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
  }

  private fun calculateDownloadDurationMillis(): Long {
    val definedEndTime = streamer.endTime ?: ""
    if (definedEndTime.isEmpty() || !isInScheduledTimeRange()) return 0L

    val currentLocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
    var jEndTime = definedEndTime.toJavaLocalDateTimeFromHM(currentLocalDateTime)

    if (jEndTime.isBefore(currentLocalDateTime) || jEndTime == currentLocalDateTime) {
      // If end time for today is already past, and we are in an overnight schedule,
      // it means the end time is on the next day.
      val definedStartTime = streamer.startTime ?: ""
      if (definedStartTime.isNotEmpty()) {
        val jStartTime = definedStartTime.toJavaLocalDateTimeFromHM(currentLocalDateTime)
        if (jStartTime.isAfter(jEndTime)) { // Overnight schedule confirmed
          jEndTime = jEndTime.plusDays(1)
        } else {
          // Same day schedule, but end time is past. No duration.
          return 0L
        }
      } else {
        // No start time, assume end time is for current day if not past, or next day if past.
        // This case is ambiguous without start time for overnight logic.
        // For safety, if end time is past, assume 0 duration unless clearly overnight.
        // Given isInScheduledTimeRange passed, this implies an overnight where current is after midnight.
        if (jEndTime.isBefore(currentLocalDateTime)) jEndTime = jEndTime.plusDays(1)
      }
    }

    val duration = java.time.Duration.between(currentLocalDateTime, jEndTime).toMillis()
    return if (duration <= 0) 0L else duration + 10000 // Add buffer
  }

  private fun String.toJavaLocalDateTimeFromHM(now: LocalDateTime): LocalDateTime {
    val parts = this.split(":")
    if (parts.size < 2) return now
    val hour = parts[0].toIntOrNull() ?: now.hour
    val min = parts[1].toIntOrNull() ?: now.minute
    val sec = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0
    return now.withHour(hour).withMinute(min).withSecond(sec).withNano(0)
  }

  private fun cleanupJobs() {
    scheduleTimerJob?.cancel()
    durationStopJob?.cancel()
    scheduleTimerJob = null
    durationStopJob = null
  }

  private fun updateErrorTracking() {
    val nowMs = Clock.System.now().toEpochMilliseconds()
    if (abs(nowMs - lastErrorTimeEpochMs) < MIN_ERROR_THRESHOLD_MS) {
      recentErrorCount++
    } else {
      resetErrorTracking()
    }
    lastErrorTimeEpochMs = nowMs
  }

  private fun resetErrorTracking() {
    lastErrorTimeEpochMs = 0
    recentErrorCount = 0
    currentErrorBackoffDelayMs = MIN_ERROR_THRESHOLD_MS
  }

  private fun getRetryDelayMs(lastError: Throwable?): Long {
    if (recentErrorCount >= ERROR_THRESHOLD_COUNT) {
      val delay = currentErrorBackoffDelayMs
      currentErrorBackoffDelayMs = (currentErrorBackoffDelayMs * 2).coerceAtMost(MAX_ERROR_DELAY_MS)
      logger.debug("${streamer.name}: Applying increased backoff delay: ${delay}ms due to $recentErrorCount recent errors.")
      return delay
    }
    return postStreamRetryDelay.inWholeMilliseconds
  }

  private suspend fun handleStreamCheckError(error: ExtractorError) {
    updateErrorTracking()
    val nextAttempt = currentRetryCountInternal + 1 // Use internal counter for attempts
    when (error) {
      is ExtractorError.StreamerBanned, is ExtractorError.StreamerNotFound -> {
        _agentState.value = AgentState.FatalError(Throwable("Streamer not found or banned: $error"))
        updateStreamerState(
          StreamerState.FATAL_ERROR,
          "Streamer not found or banned: $error"
        )
      }

      is ExtractorError.InitializationError, is ExtractorError.InvalidExtractionUrl -> {
        _agentState.value = AgentState.FatalError(Throwable("Initialization error or invalid URL: $error"))
        updateStreamerState(
          StreamerState.FATAL_ERROR,
          "Initialization error or invalid URL: $error"
        )
      }

      else -> {
        _agentState.value = AgentState.Retrying(nextAttempt, maxRetries, Throwable("Stream check error: $error"))
        // Don't call onStateChanged to NOT_LIVE here, Retrying state implies it's not successfully live.
      }
    }
  }


  /**
   * Check if the agent should react to the given engine config
   * @param engineConfig [EngineConfig] the engine config to check
   * @return Boolean true if the agent should react, false otherwise
   */
  fun shouldReactToEngineConfig(engineConfig: EngineConfig): Boolean {
    if (!isGlobalEngineConfig) return false

    // If the engine config is the same type as the streamer's engine config, we should react
    if (engineConfig.javaClass == streamer.engineConfig?.javaClass) {
      return true
    }
    // Otherwise, we should not react
    return false
  }


  /**
   * Update the engine config for the agent
   * @param engineConfig [EngineConfig] the new engine config
   */
  fun updateEngineConfig(engineConfig: EngineConfig) {
    // If the agent is not a global engine config, we should not update
    if (!isGlobalEngineConfig) return
    plugin.onEngineConfigUpdated(engineConfig)
  }


  /**
   * Change the agent state
   * @param state [AgentState] new state
   * @receiver [MutableStateFlow] agent state
   */
  private infix fun MutableStateFlow<AgentState>.to(state: AgentState) {
    value = state
  }

  private suspend fun updateStreamerState(state: StreamerState, msg: String? = null) {
    if (streamer.state != state) {
      if (state == StreamerState.LIVE) {
        EventCenter.sendEvent(
          StreamerOnline(
            streamer.name,
            streamer.url,
            streamer.platform,
            streamer.streamTitle ?: "",
            Clock.System.now()
          )
        )
      }
      // trigger the callback to update the state
      callback.onStateChanged(streamer.id, streamer.url, state, msg) {
        streamer.state = state
      }
    }
  }

  private suspend fun updateLastLiveTime() {
    val now = Clock.System.now()
    callback.onLastLiveTimeChanged(streamer.id, streamer.url, now.epochSeconds) {
      streamer.lastLiveTime = now.epochSeconds
    }
  }


}

// Helper for expected exceptions where full stack trace might be too verbose for regular logs
private open class ExpectedException(message: String) : RuntimeException(message)
private class UserStoppedDownloadException(message: String) : ExpectedException(message)
private class TimerEndedDownloadException : ExpectedException("Download timer ended")