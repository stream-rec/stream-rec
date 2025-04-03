package github.hua0512.plugins.action

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.ExecutionTiming
import github.hua0512.data.plugin.PipelineResult
import github.hua0512.data.plugin.PluginError
import github.hua0512.data.plugin.PluginStepResult
import kotlin.time.TimeSource


/**
 * Pipeline that manages the sequential execution of plugins.
 *
 * @param plugins The ordered list of plugins to execute
 * @param continueOnError Whether to continue execution if a plugin fails
 */
class PluginPipeline<T>(
  private val plugins: List<ProcessingPlugin<T, T, PluginError>>,
  private val continueOnError: Boolean = false,
) {
  /**
   * Execute the pipeline on the provided input data.
   *
   * @param initialInput The initial data to process
   * @return Result containing either the pipeline result or the first error encountered
   */
  suspend fun execute(initialInput: List<T>): Result<PipelineResult<T>, PluginError> {
    val startTimeMillis = System.currentTimeMillis()
    val timeSource = TimeSource.Monotonic
    val startMark = timeSource.markNow()

    val enabledPlugins = plugins.filter { it.enabled }
    val stepResults = mutableListOf<PluginStepResult<T, T>>()
    var currentInput = initialInput

    for (plugin in enabledPlugins) {
      // Record step start time
      val stepStartTimeMillis = System.currentTimeMillis()
      val stepStartMark = timeSource.markNow()

      // Validate the inputs first
      val validationResult = plugin.validate(currentInput)
      when {
        validationResult.isErr -> {
          if (!continueOnError) {
            return validationResult.asErr()
          }
          // Skip this plugin on validation error but continue pipeline
          continue
        }

        else -> {} // Continue with execution
      }

      // Process the inputs
      val result = plugin.process(currentInput)
      when {
        result.isOk -> {
          val output = result.value
          val stepEndTimeMillis = System.currentTimeMillis()
          val stepTiming = ExecutionTiming(
            startTime = stepStartTimeMillis,
            endTime = stepEndTimeMillis,
            duration = stepStartMark.elapsedNow()
          )

          stepResults.add(
            PluginStepResult(
              plugin = plugin,
              input = currentInput,
              output = output,
              timing = stepTiming
            )
          )

          // If the plugin produced no outputs, we can't continue the pipeline
          if (output.isEmpty()) {
            break
          }

          // Use this plugin's output as input for the next plugin
          currentInput = output
        }

        result.isErr -> {
          if (!continueOnError) {
            return result.asErr()
          }
          // Skip to next plugin but maintain the previous input
        }
      }
    }

    val endTimeMillis = System.currentTimeMillis()
    val pipelineTiming = ExecutionTiming(
      startTime = startTimeMillis,
      endTime = endTimeMillis,
      duration = startMark.elapsedNow()
    )

    return Ok(
      PipelineResult(
        steps = stepResults,
        finalOutput = currentInput,
        timing = pipelineTiming
      )
    )
  }
}
