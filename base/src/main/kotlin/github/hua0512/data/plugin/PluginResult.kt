package github.hua0512.data.plugin

import github.hua0512.plugins.action.ProcessingPlugin

/**
 * Result of a plugin pipeline execution.
 *
 * @param steps Results from each executed step in the pipeline
 * @param finalOutput The final output data produced by the pipeline
 * @param timing Overall timing information for the pipeline execution
 */
data class PipelineResult<T>(
  val steps: List<PluginStepResult<*, T>>,
  val finalOutput: List<T>,
  val timing: ExecutionTiming,
)

/**
 * Result of a single plugin step execution.
 *
 * @param plugin The plugin that was executed
 * @param input The input data provided to the plugin
 * @param output The output data produced by the plugin
 * @param timing Timing information for this step
 */
data class PluginStepResult<I, O>(
  val plugin: ProcessingPlugin<I, O, *>,
  val input: List<I>,
  val output: List<O>,
  val timing: ExecutionTiming,
)