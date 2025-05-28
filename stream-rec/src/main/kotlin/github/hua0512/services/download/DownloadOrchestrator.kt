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

import androidx.sqlite.SQLiteException
import github.hua0512.app.App
import github.hua0512.data.StreamerId
import github.hua0512.data.config.engine.DownloadEngines
import github.hua0512.data.config.engine.EngineConfig
import github.hua0512.data.event.OrchestratorEvent
import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamerState
import github.hua0512.data.stream.StreamingPlatform
import github.hua0512.flv.FlvMetaInfoProcessor
import github.hua0512.flv.data.other.FlvMetadataInfo
import github.hua0512.plugins.IOrchestrator
import github.hua0512.plugins.download.base.IPlatformDownloaderFactory
import github.hua0512.plugins.download.base.StreamerCallback
import github.hua0512.plugins.download.fillDownloadConfig
import github.hua0512.plugins.download.globalConfig
import github.hua0512.repo.config.EngineConfigManager
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.services.ActionService
import github.hua0512.utils.MemoryStreamerPool
import github.hua0512.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class DownloadOrchestrator(
  private val app: App,
  private val streamerRepo: StreamerRepo,
  private val streamDataRepo: StreamDataRepo,
  private val engineConfigManager: EngineConfigManager,
  private val actionService: ActionService,
  private val downloaderFactory: IPlatformDownloaderFactory = PlatformDownloaderFactory
) : StreamerCallback, IOrchestrator {

  companion object {
    @JvmStatic
    private val logger: Logger = logger(DownloadOrchestrator::class.java)
    private const val AGENT_STARTUP_INTERVAL_MS = 200L // Delay between starting agents
  }

  private lateinit var orchestratorScope: CoroutineScope
  private lateinit var downloadSemaphore: Semaphore

  // Store agent instance and its main job
  private val activeAgents = ConcurrentHashMap<Long, Pair<StreamerDownloadAgent, Job>>()

  private val streamerPool = MemoryStreamerPool()

  private val platformRateLimiters = ConcurrentHashMap<StreamingPlatform, RateLimiter>()
  private val agentActivationChannel = Channel<Streamer>(Channel.UNLIMITED)
  private val agentManagementMutex = Mutex()


  suspend fun run(scope: CoroutineScope) {
    this.orchestratorScope =
      CoroutineScope(scope.coroutineContext + SupervisorJob() + CoroutineName("DownloadOrchestratorScope"))
    downloadSemaphore = Semaphore(app.config.maxConcurrentDownloads)

    logger.info("DownloadOrchestrator starting...")

    // Initialize rate limiters
    StreamingPlatform.entries.forEach { platform ->
      if (platform == StreamingPlatform.UNKNOWN) {
        return@forEach
      }
      val fetchDelayMs = (platform.globalConfig(app.config).fetchDelay ?: 0)
        .toDuration(DurationUnit.SECONDS).inWholeMilliseconds
      platformRateLimiters[platform] = RateLimiter(1, fetchDelayMs.coerceAtLeast(0))
    }

    // Load initial streamers
    loadInitialStreamers()

    // Start listeners and processors
    listenToAppConfigChanges()
    listenToEngineConfigChanges()
    processAgentActivationChannel()

    logger.info("DownloadOrchestrator running.")
  }


  override suspend fun onEvent(event: OrchestratorEvent): Boolean {
    logger.debug("Received orchestrator event: {}", event)
    agentManagementMutex.withLock {
      when (event) {
        is OrchestratorEvent.StreamerAdded -> handleNewStreamerEvent(event.streamer)
        is OrchestratorEvent.StreamerRemoved -> handleDeletedStreamerEvent(event.streamer)
        is OrchestratorEvent.StreamerUpdated -> {
          val new = event.streamer

          logger.info("Streamer configuration updated for: ${new.name} (ID: ${new.id})")
          val old = streamerPool[new.url] ?: run {
            logger.info("Streamer not found in pool for URL: ${new.url}... Adding as new streamer.")
            handleNewStreamerEvent(new)
            return@withLock true
          }


          if (new.state == StreamerState.CANCELLED) {
            logger.info("Streamer ${new.name} (ID: ${new.id}) is now CANCELLED. Stopping agent if exists.")
            handleDeletedStreamerEvent(old)
            return@withLock true
          }


          // check if the new streamer is different from the current one
          if (old == new) {
            logger.debug("No changes detected in streamer ${new.name} (ID: ${new.id}). Skipping update.")
            return@withLock false
          }

          // streamer changed
          val reason = when {
            old.url != new.url -> "url"
            old.downloadConfig != new.downloadConfig -> "download config"
            old.platform != new.platform -> "platform"
            old.name != new.name -> "name"
            old.isTemplate != new.isTemplate -> "as template"
            old.templateId != new.templateId -> "template id"
            old.startTime != new.startTime -> "start time"
            old.endTime != new.endTime -> "end time"
            old.templateStreamer?.downloadConfig != new.templateStreamer?.downloadConfig -> "template stream download config"
            old.engine != new.engine -> "engine"
            old.engineConfig != new.engineConfig -> "engine config"
            old.state != new.state -> when {
              new.state == StreamerState.CANCELLED && old.state != StreamerState.CANCELLED -> "cancelled"
              new.state == StreamerState.NOT_LIVE && old.state == StreamerState.CANCELLED -> "enabled"
              else -> return@withLock false // No significant change
            }

            else -> { // No significant change detected
              return@withLock false
            }
          }


          // update pool with the new streamer
          streamerPool[new.url] = new
          logger.debug("Updated streamer pool with ${new.name} (ID: ${new.id}) due to $reason change.")
          // trigger cancellation and re-activation
          handleDeletedStreamerEvent(old)
          handleNewStreamerEvent(new)
        }
      }
    }
    return true
  }


  private fun handleNewStreamerEvent(newStreamer: Streamer) {
    logger.info("New streamer detected: ${newStreamer.name} (ID: ${newStreamer.id}), url: ${newStreamer.url}")
    streamerPool += newStreamer

    // Don't activate if marked as cancelled
    if (newStreamer.state != StreamerState.CANCELLED) {
      agentActivationChannel.trySend(newStreamer)
    }
  }

  private suspend fun handleDeletedStreamerEvent(deletedStreamer: Streamer) {
    logger.info("Streamer removed: ${deletedStreamer.name} (ID: ${deletedStreamer.id})")
    cancelAndRemoveAgent(deletedStreamer.id, "Removed from orchestrator event")
    streamerPool -= deletedStreamer.url
  }

  private suspend fun Streamer.populateConfigs(): Streamer {
    // Fill download config if not already set
    val newDownloadConfig = downloadConfig?.fillDownloadConfig(
      platform,
      templateStreamer?.downloadConfig,
      app.config
    )

    // Get engine from streamer or app config
    val streamerEngine: DownloadEngines = engine ?: DownloadEngines.fromString(app.config.engine)

    // Get the engine config from the manager or use the one from the streamer
    val newEngineConfig =
      engine?.run { this@populateConfigs.engineConfig } ?: engineConfigManager.getEngineConfig<EngineConfig>(
        app.config.id,
        streamerEngine.engine
      )

    // update the streamer with the new download config and engine
    val updatedStreamer = this.copy(
      downloadConfig = newDownloadConfig,
      engine = streamerEngine,
      engineConfig = newEngineConfig,
    )
    return updatedStreamer
  }


  private fun loadInitialStreamers() {
    orchestratorScope.launch(Dispatchers.IO) {
      logger.debug("Loading initial streamers from database...")
      val initialStreamers = streamerRepo.getStreamersActive().filterNot { it.isTemplate }
      agentManagementMutex.withLock {
        initialStreamers.forEach { streamer ->
          streamerPool.add(streamer)
          logger.debug("Enqueueing initial streamer for activation: ${streamer.name} (ID: ${streamer.id})")
          agentActivationChannel.trySend(streamer)
        }
      }
      logger.info("Loaded ${initialStreamers.size} initial active streamers.")
    }
  }


  private fun processAgentActivationChannel() {
    // Use Default dispatcher for potentially blocking rate limiter
    orchestratorScope.launch(Dispatchers.Default) {
      for (streamer in agentActivationChannel) {
        if (!isActive) break // Orchestrator scope cancelled

        if (activeAgents.containsKey(streamer.id)) {
          logger.debug("Agent for ${streamer.name} (ID: ${streamer.id}) already active or being processed. Skipping activation.")
          continue
        }
        if (streamer.state == StreamerState.CANCELLED) {
          logger.info("Streamer ${streamer.name} (ID: ${streamer.id}) is marked as CANCELLED. Skipping activation.")
          continue
        }

        val rateLimiter = platformRateLimiters[streamer.platform]
//        if (rateLimiter != null) {
//          logger.debug("Acquiring rate limit permit for {} for streamer {}", streamer.platform, streamer.name)
//          rateLimiter.acquire() // This will suspend if rate limit is hit
//          logger.debug("Rate limit permit acquired for {} for streamer {}", streamer.platform, streamer.name)
//        } else {
//          logger.warn("No rate limiter found for platform: ${streamer.platform}. Proceeding without rate limiting.")
//        }

        if (!isActive) break // Check again after potential suspension

        launchAgentFor(streamer, rateLimiter)

        // Stagger agent starts
        delay(AGENT_STARTUP_INTERVAL_MS)
      }
      logger.info("Agent activation channel processing stopped.")
    }
  }

  private suspend fun launchAgentFor(streamer: Streamer, rateLimiter: RateLimiter? = null) {
    agentManagementMutex.withLock {
      if (activeAgents.containsKey(streamer.id)) {
        logger.warn("Attempted to launch agent for ${streamer.name} (ID: ${streamer.id}), but one already exists.")
        return
      }
      if (streamer.state == StreamerState.CANCELLED) {
        logger.info("Streamer ${streamer.name} (ID: ${streamer.id}) is CANCELLED, not launching agent.")
        return
      }

      logger.info("Launching agent for ${streamer.name} (ID: ${streamer.id}) on platform ${streamer.platform}")
      try {
        val plugin = downloaderFactory.createDownloader(app, streamer.platform, streamer.url)
        val agentScope =
          CoroutineScope(orchestratorScope.coroutineContext + SupervisorJob() + CoroutineName("Agent-${streamer.name}"))

        // populate platform configs
        val updatedStreamer = streamer.populateConfigs()
        val agent =
          StreamerDownloadAgent(app, updatedStreamer, plugin, this, downloadSemaphore, rateLimiter, agentScope)

        agent.isGlobalEngineConfig = streamer.engine?.engine == null

        val job = agentScope.launch {
          try {
            agent.start()
          } catch (e: Exception) {
            if (e is CancellationException) {
              logger.info("Agent job for ${streamer.name} (ID: ${streamer.id}) cancelled.")
            } else {
              logger.error("Unhandled exception in agent job for ${streamer.name} (ID: ${streamer.id}): $e", e)
            }
          } finally {
            // Agent job completion or cancellation
            logger.info("Agent job for ${streamer.name} (ID: ${streamer.id}) finished. Final agent state: ${agent.agentState.value}")
            agentManagementMutex.withLock {
              activeAgents.remove(streamer.id)
            }
          }
        }
        activeAgents[streamer.id] = Pair(agent, job)
        logger.info("Agent for ${streamer.name} (ID: ${streamer.id}) launched successfully.")
      } catch (e: Exception) {
        logger.error("Failed to create or launch agent for ${streamer.name} (ID: ${streamer.id}): $e", e)
        // Potentially update streamer state to FATAL_ERROR in DB if creation fails persistently
        onStateChanged(streamer.id, streamer.url, StreamerState.FATAL_ERROR, "Agent creation failed: ${e.message}") {}
      }
    }
  }

  private suspend fun cancelAndRemoveAgent(streamerId: Long, reason: String) {
    val agentPair = activeAgents.remove(streamerId)
    if (agentPair != null) {
      val (agent, job) = agentPair
      logger.info("Cancelling agent for streamer ID $streamerId due to: $reason. Current agent state: ${agent.agentState.value}")
      agent.stopBlocking()
      job.cancel("Cancelled by orchestrator: $reason")
      logger.info("Agent for streamer ID $streamerId removed.")
    } else {
      logger.debug("No active agent found for streamer ID $streamerId to cancel.")
    }
  }


  private fun listenToAppConfigChanges() {
    orchestratorScope.launch {
      app.appFlow.filterNotNull().collect { newAppConfig ->
        logger.info("App configuration changed. Updating orchestrator and agents.")
        // Update semaphore
        val newMaxConcurrent = newAppConfig.maxConcurrentDownloads
//                if (newMaxConcurrent != downloadSemaphore.totalPermits) {
//                    logger.info("Updating max concurrent downloads from ${downloadSemaphore.totalPermits} to $newMaxConcurrent")
//                    // Note: Directly changing semaphore permits is tricky.
//                    // A common approach is to replace the semaphore, but this needs careful handling
//                    // of permits already acquired. For simplicity, we might log and require restart,
//                    // or implement more complex permit migration.
//                    // For now, let's assume we create a new one and agents will pick it up on next acquire.
//                    // This is a simplification; robust semaphore resizing is non-trivial.
//                    downloadSemaphore = Semaphore(newMaxConcurrent)
//                     logger.warn("Semaphore permit size changed. Active downloads might not respect new limit until they release and re-acquire permits.")
//                }

        // Update rate limiters
        StreamingPlatform.entries.forEach { platform ->
          if (platform == StreamingPlatform.UNKNOWN) {
            return@forEach
          }
          val fetchDelayMs = (platform.globalConfig(newAppConfig).fetchDelay ?: 0)
            .toDuration(DurationUnit.SECONDS).inWholeMilliseconds
          platformRateLimiters[platform]?.updateMinDelay(fetchDelayMs.coerceAtLeast(0))
            ?: run {
              logger.info("Creating new rate limiter for platform ${platform.name} with fetch delay $fetchDelayMs ms")
              platformRateLimiters[platform] = RateLimiter(1, fetchDelayMs.coerceAtLeast(0))
            }
        }

        // Propagate to agents
        activeAgents.values.parallelStream()
          .forEach { (agent, _) ->
            agent.updateAppConfig(newAppConfig)
          }
      }
    }
  }

  private fun listenToEngineConfigChanges() {
    orchestratorScope.launch {
      // engineConfigManager.streamAllConfigs() provides a Flow of all engine configs
      // TODO : A more granular approach would be better if possible.
      engineConfigManager.streamConfigs(app.config.id)
        .collect { engineConfig ->
          logger.info("Engine configuration changed for ${engineConfig::class.simpleName}. Propagating to relevant agents.")
          activeAgents.values.forEach { (agent, _) ->
            // Check if this engineConfig is relevant to it
            if (agent.shouldReactToEngineConfig(engineConfig)
            ) {
              agent.updateEngineConfig(engineConfig)
            }
          }
        }
    }
  }

  fun shutdown() {
    logger.info("DownloadOrchestrator shutting down...")
    // Stop new activations
    agentActivationChannel.close()
    // Launch in orchestratorScope to ensure it completes during shutdown sequence
    orchestratorScope.launch {
      val shutdownJobs = mutableListOf<Job>()
      activeAgents.keys.forEach { streamerId ->
        shutdownJobs.add(launch { cancelAndRemoveAgent(streamerId, "Orchestrator shutdown") })
      }
      // Wait for all agents to be cancelled
      shutdownJobs.joinAll()
      logger.info("All active agents cancelled.")
    }
    orchestratorScope.cancel("DownloadOrchestrator is shutting down.")
    logger.info("DownloadOrchestrator shutdown complete.")
  }

  // --- StreamerCallback Implementation ---

  override suspend fun onStateChanged(
    streamerId: Long,
    streamerUrl: String,
    newState: StreamerState,
    message: String?,
    onSuccessful: () -> Unit
  ) {
    withIOContext(CoroutineName("Orch-StateChange-$streamerId")) {
      val streamer = streamerPool[streamerUrl] ?: streamerRepo.getStreamerById(StreamerId(streamerId))
      if (streamer == null) {
        logger.warn("onStateChanged: Streamer with ID $streamerId not found in cache or repo.")
        return@withIOContext
      }

      if (streamer.state == newState) {
        onSuccessful()
        return@withIOContext
      }

      val updatedStreamer = streamer.copy(state = newState)
      val success = streamerRepo.update(updatedStreamer)
      if (success) {
        logger.debug(
          "Streamer {} (ID: {}) state updated to {} in DB. Message: {}",
          streamer.name,
          streamerId,
          newState,
          message
        )

        streamerPool[streamerUrl] = updatedStreamer
        onSuccessful()
      } else {
        logger.error("Failed to update state for streamer ${streamer.name} (ID: $streamerId) to $newState in DB.")
      }
    }
  }

  override suspend fun onLastLiveTimeChanged(
    streamerId: Long,
    streamerUrl: String,
    newLiveTimeEpochSeconds: Long,
    onSuccessful: () -> Unit
  ) {
    withIOContext(CoroutineName("Orch-LastLiveTime-$streamerId")) {
      val streamer = streamerPool[streamerUrl] ?: streamerRepo.getStreamerById(StreamerId(streamerId))
      if (streamer == null) {
        logger.warn("onLastLiveTimeChanged: Streamer with ID $streamerId not found.")
        return@withIOContext
      }
      if (streamer.lastLiveTime == newLiveTimeEpochSeconds) {
        onSuccessful(); return@withIOContext
      }
      val updatedStreamer = streamer.copy(lastLiveTime = newLiveTimeEpochSeconds)
      val success = streamerRepo.update(updatedStreamer)
      if (success) {
        logger.debug("Streamer ${streamer.name} (ID: $streamerId) last live time updated to $newLiveTimeEpochSeconds.")
        streamerPool[streamerUrl] = updatedStreamer
        onSuccessful()
      } else {
        logger.error("Failed to update last live time for streamer ${streamer.name} (ID: $streamerId).")
      }
    }
  }

  override suspend fun onDescriptionChanged(
    streamerId: Long,
    streamerUrl: String,
    description: String,
    onSuccessful: () -> Unit
  ) {
    withIOContext(CoroutineName("Orch-DescriptionChange-$streamerId")) {
      val streamer = streamerPool[streamerUrl] ?: streamerRepo.getStreamerById(StreamerId(streamerId))
      if (streamer == null) {
        logger.warn("onDescriptionChanged: Streamer with ID $streamerId not found.")
        return@withIOContext
      }
      if (streamer.streamTitle == description) {
        onSuccessful(); return@withIOContext
      }
      val updatedStreamer = streamer.copy(streamTitle = description)
      val success = streamerRepo.update(updatedStreamer)
      if (success) {
        logger.debug("Streamer ${streamer.name} (ID: $streamerId) description updated.")
        streamerPool[streamerUrl] = updatedStreamer
        onSuccessful()
      } else {
        logger.error("Failed to update description for streamer ${streamer.name} (ID: $streamerId).")
      }
    }
  }

  override suspend fun onAvatarChanged(
    streamerId: Long,
    streamerUrl: String,
    avatarUrl: String,
    onSuccessful: () -> Unit
  ) {
    withIOContext(CoroutineName("Orch-AvatarChange-$streamerId")) {
      val streamer = streamerPool[streamerUrl] ?: streamerRepo.getStreamerById(StreamerId(streamerId))
      if (streamer == null) {
        logger.warn("onAvatarChanged: Streamer with ID $streamerId not found.")
        return@withIOContext
      }
      if (streamer.avatar == avatarUrl) {
        onSuccessful(); return@withIOContext
      }
      val updatedStreamer = streamer.copy(avatar = avatarUrl)
      val success = streamerRepo.update(updatedStreamer)
      if (success) {
        logger.debug("Streamer ${streamer.name} (ID: $streamerId) avatar updated.")
        streamerPool[streamerUrl] = updatedStreamer
        onSuccessful()
      } else {
        logger.error("Failed to update avatar for streamer ${streamer.name} (ID: $streamerId).")
      }
    }
  }

  override fun onStreamDownloaded(
    streamerId: Long,
    streamerUrl: String,
    streamData: StreamData,
    shouldInjectMetaInfo: Boolean,
    metaInfo: FlvMetadataInfo?
  ) {
    orchestratorScope.launch(Dispatchers.IO + CoroutineName("Orch-StreamDownloaded-$streamerId")) {
      var finalStreamData = streamData
      val streamer = streamerPool[streamerUrl] ?: streamerRepo.getStreamerById(StreamerId(streamerId))
      if (streamer == null) {
        logger.error("onStreamDownloaded: Streamer with ID $streamerId not found for stream data: ${streamData.outputFilePath}")
        return@launch
      }

      if (shouldInjectMetaInfo && metaInfo != null) {
        logger.debug("Injecting FlvMetadata for ${finalStreamData.outputFilePath}")
        val status = FlvMetaInfoProcessor.process(finalStreamData.outputFilePath, metaInfo, true)
        if (status) {
          logger.info("${finalStreamData.outputFilePath} metadata injected successfully.")
          finalStreamData = finalStreamData.copy(outputFileSize = Path(finalStreamData.outputFilePath).fileSize())
        } else {
          logger.error("Failed to inject metadata for ${finalStreamData.outputFilePath}")
        }
      }

      val savedStreamDataId = withRetry<SQLiteException, Long>(
        maxRetries = 3,
        initialDelayMillis = 1000,
        onError = { e, count ->
          logger.error(
            "Failed to save stream data for ${streamer.name} (attempt $count): ${e.message}",
            e
          )
        }
      ) {
        streamDataRepo.save(finalStreamData).id
      }

      if (savedStreamDataId != -1L) { // Assuming -1 or similar for failure if not throwing
        finalStreamData = finalStreamData.copy(id = savedStreamDataId)
        logger.info("Stream data saved for ${streamer.name}: ${finalStreamData.outputFilePath} (ID: $savedStreamDataId)")
        executePostPartedDownloadActions(streamer, finalStreamData)
      } else {
        logger.error("Failed to save stream data for ${streamer.name} after retries: ${finalStreamData.outputFilePath}")
      }
    }
  }

  override fun onStreamDownloadFailed(
    streamerId: Long,
    streamerUrl: String,
    stream: StreamData?,
    exception: Exception
  ) {
    orchestratorScope.launch(Dispatchers.IO + CoroutineName("Orch-StreamDownloadFailed-$streamerId")) {
      val streamer = streamerPool[streamerUrl] ?: streamerRepo.getStreamerById(StreamerId(streamerId))
      logger.error(
        "Stream download failed for ${streamer?.name ?: "ID $streamerId"}. File: ${stream?.outputFilePath ?: "N/A"}",
        exception
      )
      // Further actions can be added here, e.g., notifying, specific cleanup
    }
  }

  override fun onStreamFinished(streamerId: Long, streamerUrl: String, streams: List<StreamData>) {
    orchestratorScope.launch(Dispatchers.IO + CoroutineName("Orch-StreamFinished-$streamerId")) {
      val streamer = streamerPool[streamerUrl] ?: streamerRepo.getStreamerById(StreamerId(streamerId))
      if (streamer == null) {
        logger.error("onStreamFinished: Streamer with ID $streamerId not found.")
        return@launch
      }
      logger.info("Stream finished for ${streamer.name}. ${streams.size} segments processed.")
      // Ensure final state is NOT_LIVE if not already CANCELLED or FATAL_ERROR
      if (streamer.state != StreamerState.NOT_LIVE && streamer.state != StreamerState.CANCELLED && streamer.state != StreamerState.FATAL_ERROR) {
        onStateChanged(streamerId, streamerUrl, StreamerState.NOT_LIVE, "Stream recording session finished.") {}
      }
      executeStreamFinishedActions(streamer, streams)
    }
  }

  private suspend fun executePostPartedDownloadActions(streamer: Streamer, streamData: StreamData) {
    val actions = streamer.templateStreamer?.downloadConfig?.onPartedDownload
      ?: streamer.downloadConfig?.onPartedDownload
    actions?.let {
      if (it.isNotEmpty()) {
        logger.debug("Executing onPartedDownload actions for ${streamer.name}, data: ${streamData.outputFilePath}")
        actionService.runActions(listOf(streamData), it)
      }
    }
  }

  private suspend fun executeStreamFinishedActions(streamer: Streamer, streamDataList: List<StreamData>) {
    val actions = streamer.templateStreamer?.downloadConfig?.onStreamingFinished
      ?: streamer.downloadConfig?.onStreamingFinished
    actions?.let {
      if (it.isNotEmpty()) {
        logger.debug("Executing onStreamingFinished actions for ${streamer.name}")
        actionService.runActions(streamDataList, it)
      }
    } ?: run {
      // Default behavior if no onStreamingFinished actions: check onPartedDownload
      val partedActions = streamer.templateStreamer?.downloadConfig?.onPartedDownload
        ?: streamer.downloadConfig?.onPartedDownload
      if (partedActions.isNullOrEmpty()) {
        // If both are empty, consider deleting files based on global config
        if (app.config.deleteFilesAfterUpload) { // Assuming "upload" implies general post-processing completion
          logger.info("No post-download actions defined for ${streamer.name} and deleteFilesAfterUpload is true. Deleting files.")
          streamDataList.forEach { Path(it.outputFilePath).deleteFile() }
        }
      }
    }
  }
}