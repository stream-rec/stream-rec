package github.hua0512.plugins.action

import com.github.michaelbull.result.Result
import github.hua0512.data.plugin.ExecutionTiming
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginError
import github.hua0512.plugins.action.base.Plugin

/**
 * Plugin that processes input data and produces output data.
 *
 * @param I The type of input data.
 * @param O The type of output data.
 * @param E The type of error that can occur during processing.
 */
interface ProcessingPlugin<I, O, E : PluginError> : Plugin {

  /**
   * Validates the inputs before processing.
   *
   * @param inputs The inputs to validate.
   * @return Success with Unit or an error if validation fails.
   */
  suspend fun validate(inputs: List<I>): Result<Unit, E>

  /**
   * Processes a list of inputs sequentially or in parallel.
   *
   * @param inputs The inputs to process.
   * @return A result containing either a list of outputs or an error.
   */
  suspend fun process(inputs: List<I>): Result<List<O>, E>

  /**
   * Called before the plugin executes.
   *
   * @param inputs The inputs that will be processed.
   */
  suspend fun preExecution(inputs: List<I>)

  /**
   * Called after successful execution.
   *
   * @param outputs The outputs produced by the plugin.
   * @param timing Timing information for the execution.
   */
  suspend fun onExecutionSuccess(outputs: List<O>, timing: ExecutionTiming)

  /**
   * Called if execution fails.
   *
   * @param error The error that occurred during execution.
   * @param timing Timing information for the execution.
   */
  suspend fun onExecutionError(error: E, timing: ExecutionTiming)

  /**
   * Called after a single item was processed successfully.
   *
   * @param input The input that was processed.
   * @param outputs The outputs produced by processing this input.
   * @param timing Timing information for this specific item's processing.
   */
  suspend fun onItemSuccess(input: I, outputs: List<O>, timing: ItemExecutionTiming)

  /**
   * Called when a single item processing failed.
   *
   * @param input The input that failed processing.
   * @param error The error that occurred during processing.
   * @param timing Timing information for this specific item's processing.
   */
  suspend fun onItemError(input: I, error: E, timing: ItemExecutionTiming)

  /**
   * Determines if this plugin processes inputs in parallel.
   */
  val processInParallel: Boolean
}
