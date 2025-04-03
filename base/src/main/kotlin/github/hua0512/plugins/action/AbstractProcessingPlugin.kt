package github.hua0512.plugins.action

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import github.hua0512.data.plugin.ExecutionTiming
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginError
import github.hua0512.data.dto.IOutputFile
import github.hua0512.utils.replacePlaceholders
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Abstract implementation of a processing plugin with common functionality.
 */
abstract class AbstractProcessingPlugin<I, O, E : PluginError> : ProcessingPlugin<I, O, E> {

  companion object {
    private val logger = LoggerFactory.getLogger(AbstractProcessingPlugin::class.java)
  }

  override var enabled: Boolean = true

  override val processInParallel: Boolean = false

  /**
   * Process a single input item.
   *
   * @param input The input to process.
   * @return Result containing either a list of outputs or an error.
   */
  protected abstract suspend fun processItem(input: I): Result<List<O>, E>


  override suspend fun process(inputs: List<I>): Result<List<O>, E> {
    if (!enabled) {
      return Ok(inputs as List<O>) // Pass through if disabled
    }

    val startTimeMillis = System.currentTimeMillis()
    val timeSource = TimeSource.Monotonic
    val startMark = timeSource.markNow()

    return try {
      // Call pre-execution hook
      preExecution(inputs)

      // Process all inputs
      val result = if (processInParallel) {
        processParallel(inputs, timeSource, startTimeMillis)
      } else {
        processSequential(inputs, timeSource, startTimeMillis)
      }

      logger.debug(
        "{} Finished processing inputs: {}, result: {}",
        this::class.simpleName,
        inputs,
        result
      )

      val endTimeMillis = System.currentTimeMillis()
      val duration = startMark.elapsedNow()
      val timing = ExecutionTiming(
        startTime = startTimeMillis,
        endTime = endTimeMillis,
        duration = duration
      )

      when {
        result.isOk -> {
          onExecutionSuccess(result.value, timing)
          result
        }

        result.isErr -> {
          onExecutionError(result.error, timing)
          result
        }

        else -> throw IllegalStateException("Unexpected result type")
      }
    } catch (e: Exception) {
      val endTimeMillis = System.currentTimeMillis()
      val duration = startMark.elapsedNow()
      val timing = ExecutionTiming(
        startTime = startTimeMillis,
        endTime = endTimeMillis,
        duration = duration
      )

      val error = createExecutionError("Unexpected error during processing: ${e.message}", e)
      onExecutionError(error, timing)
      Err(error)
    }
  }

  private suspend fun processSequential(
    inputs: List<I>,
    timeSource: TimeSource,
    globalStartTime: Long,
  ): Result<List<O>, E> {
    val outputs = mutableListOf<O>()

    inputs.forEachIndexed { index, input ->
      val itemStartTimeMillis = System.currentTimeMillis()
      val itemStartMark = timeSource.markNow()

      try {
        logger.debug("{} Processing item at index {}: {}", this::class.simpleName, index, input)
        val result = processItem(input)
        when {
          result.isOk -> {
            val itemEndTimeMillis = System.currentTimeMillis()
            val itemDuration = itemStartMark.elapsedNow()
            val itemTiming = ItemExecutionTiming(
              startTime = itemStartTimeMillis,
              endTime = itemEndTimeMillis,
              duration = itemDuration,
              itemIndex = index
            )

            onItemSuccess(input, result.value, itemTiming)
            // let's clear the outputs list before adding new items
            // use the final outputs list to avoid duplicates
//            outputs.clear()
            outputs.addAll(result.value)
            logger.debug(
              "{} Finished processing item at index {}: {}, results: {}",
              this::class.simpleName,
              index,
              input,
              result
            )
          }

          result.isErr -> {
            val itemEndTimeMillis = System.currentTimeMillis()
            val itemDuration = itemStartMark.elapsedNow()
            val itemTiming = ItemExecutionTiming(
              startTime = itemStartTimeMillis,
              endTime = itemEndTimeMillis,
              duration = itemDuration,
              itemIndex = index
            )

            onItemError(input, result.error, itemTiming)
            return Err(result.error)
          }
        }
      } catch (e: Exception) {
        val itemEndTimeMillis = System.currentTimeMillis()
        val itemDuration = itemStartMark.elapsedNow()
        val itemTiming = ItemExecutionTiming(
          startTime = itemStartTimeMillis,
          endTime = itemEndTimeMillis,
          duration = itemDuration,
          itemIndex = index
        )

        val error = createExecutionError("Error processing item at index $index: ${e.message}", e)
        onItemError(input, error, itemTiming)
        return Err(error)
      }
    }

    return Ok(outputs)
  }

  private suspend fun processParallel(
    inputs: List<I>,
    timeSource: TimeSource,
    globalStartTime: Long,
  ): Result<List<O>, E> = coroutineScope {
    val itemTimings = mutableMapOf<Int, Pair<Long, TimeMark>>()

    // Record start times for all items
    inputs.forEachIndexed { index, _ ->
      itemTimings[index] = System.currentTimeMillis() to timeSource.markNow()
    }

    val deferredResults = inputs.mapIndexed { index, input ->
      async {
        try {
          processItem(input) to index
        } catch (e: Exception) {
          Err(createExecutionError("Error processing item at index $index: ${e.message}", e)) to index
        }
      }
    }.awaitAll()

    // Process all results and call appropriate callbacks
    val outputs = mutableListOf<O>()
    var firstError: PluginError? = null

    for ((result, index) in deferredResults) {
      val (startTimeMillis, startMark) = itemTimings[index]!!
      val endTimeMillis = System.currentTimeMillis()
      val itemTiming = ItemExecutionTiming(
        startTime = startTimeMillis,
        endTime = endTimeMillis,
        duration = startMark.elapsedNow(),
        itemIndex = index
      )


      when {
        result.isOk -> {
          onItemSuccess(inputs[index], result.value, itemTiming)
          outputs.addAll(result.value)
        }

        result.isErr -> {
          onItemError(inputs[index], result.error, itemTiming)
          if (firstError == null) {
            firstError = result.error
          }
        }
      }
    }

    if (firstError != null) {
      Err(firstError as E)
    } else {
      Ok(outputs)
    }
  }

  /**
   * Create an execution error. Should be implemented by subclasses to
   * return the appropriate error type.
   */
  protected abstract fun createExecutionError(message: String, cause: Throwable? = null): E

  // Default implementations of hooks that can be overridden

  override suspend fun preExecution(inputs: List<I>) {
    // Default implementation does nothing
  }

  override suspend fun onExecutionSuccess(outputs: List<O>, timing: ExecutionTiming) {
    // Default implementation does nothing
  }

  override suspend fun onExecutionError(error: E, timing: ExecutionTiming) {
    // Default implementation does nothing
  }

  override suspend fun onItemSuccess(input: I, outputs: List<O>, timing: ItemExecutionTiming) {
    // Default implementation does nothing
  }

  override suspend fun onItemError(input: I, error: E, timing: ItemExecutionTiming) {
    // Default implementation does nothing
  }

  open fun String.replacePlaceholders(sourceFile: IOutputFile): String = this.replacePlaceholders(
    sourceFile.streamerName.toString(),
    title = sourceFile.streamTitle.toString(),
    sourceFile.streamerPlatform,
    Instant.fromEpochSeconds(sourceFile.streamDate ?: Clock.System.now().epochSeconds),
  )
}
