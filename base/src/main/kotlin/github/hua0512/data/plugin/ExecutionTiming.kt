package github.hua0512.data.plugin

import kotlin.time.Duration

/**
 * Represents timing information for overall plugin execution.
 *
 * @param startTime The timestamp when execution started (milliseconds since epoch)
 * @param endTime The timestamp when execution ended (milliseconds since epoch)
 * @param duration The total execution duration
 */
data class ExecutionTiming(
  val startTime: Long,
  val endTime: Long,
  val duration: Duration,
)

/**
 * Represents timing information for a single item's execution.
 *
 * @param startTime The timestamp when item processing started (milliseconds since epoch)
 * @param endTime The timestamp when item processing ended (milliseconds since epoch)
 * @param duration The processing duration for this item
 * @param itemIndex The index of this item in the original input list
 */
data class ItemExecutionTiming(
  val startTime: Long,
  val endTime: Long,
  val duration: Duration,
  val itemIndex: Int,
)
