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

package github.hua0512.flv.operators

import github.hua0512.flv.data.FlvData
import github.hua0512.flv.data.FlvTag
import github.hua0512.flv.data.tag.FlvTagHeaderType
import github.hua0512.plugins.StreamerContext
import github.hua0512.utils.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*
import kotlin.math.*

private const val TAG = "FlvDuplicateRule"
private val logger = logger(TAG)

/**
 * Quality level for the stream
 */
enum class StreamQuality {
  LOW,    // Low quality, more tolerant of duplicates
  MEDIUM, // Balanced quality
  HIGH    // High quality, less tolerant of duplicates
}

/**
 * Network condition status
 */
enum class NetworkCondition {
  POOR,     // High latency, packet loss
  MODERATE, // Some issues but stable
  GOOD      // Low latency, stable connection
}

/**
 * Configuration for duplicate detection that adapts to conditions
 */
data class DuplicateConfig(
  val networkCondition: NetworkCondition = NetworkCondition.MODERATE,
  private val quality: StreamQuality = StreamQuality.MEDIUM,
  private val systemResources: SystemResources = SystemResources.DEFAULT,
  val timeWindow: Long = BASE_TIME_DIFFERENCE[quality] ?: 500L,
  val similarityThreshold: Double = 0.8,
) {
  companion object {
    // Base values for different quality levels
    private val BASE_CONSECUTIVE_DUPLICATES = mapOf(
      StreamQuality.LOW to 15,
      StreamQuality.MEDIUM to 10,
      StreamQuality.HIGH to 5
    )

    private val BASE_TIME_DIFFERENCE = mapOf(
      StreamQuality.LOW to 1000L,
      StreamQuality.MEDIUM to 500L,
      StreamQuality.HIGH to 250L
    )

    private val BASE_HISTORY_SIZE = mapOf(
      StreamQuality.LOW to 60,
      StreamQuality.MEDIUM to 100,
      StreamQuality.HIGH to 150
    )

    // Network condition multipliers
    private val NETWORK_MULTIPLIERS = mapOf(
      NetworkCondition.POOR to 2.0,
      NetworkCondition.MODERATE to 1.0,
      NetworkCondition.GOOD to 0.5
    )
  }

  /**
   * Calculate maximum consecutive duplicates based on conditions
   */
  val maxConsecutiveDuplicates: Int
    get() {
      val base = BASE_CONSECUTIVE_DUPLICATES[quality] ?: 10
      val multiplier = NETWORK_MULTIPLIERS[networkCondition] ?: 1.0
      return (base * multiplier).toInt()
    }

  /**
   * Calculate maximum time difference based on conditions
   */
  val maxTimeDifference: Long
    get() {
      val base = BASE_TIME_DIFFERENCE[quality] ?: 500L
      val multiplier = NETWORK_MULTIPLIERS[networkCondition] ?: 1.0
      return (base * multiplier).toLong()
    }

  /**
   * Calculate history size based on conditions and available memory
   */
  val historySize: Int
    get() {
      val base = BASE_HISTORY_SIZE[quality] ?: 100
      // Adjust based on available memory
      return when {
        systemResources.availableMemoryPercent > 70 -> base * 2  // Plenty of memory
        systemResources.availableMemoryPercent > 40 -> base      // Normal memory
        else -> max(30, base / 2)                                // Low memory
      }
    }

  fun withDegradedConditions(): DuplicateConfig {
    return copy(
      networkCondition = NetworkCondition.POOR,
      similarityThreshold = similarityThreshold * 0.9,
      timeWindow = timeWindow * 1.5.toLong()
    )
  }

  val currentThresholds: String
    get() = "maxDiff=${maxTimeDifference}ms, similarity=$similarityThreshold"
}

/**
 * System resources information
 */
data class SystemResources(
  val availableMemoryPercent: Int,
  val cpuUsagePercent: Int,
) {
  companion object {
    val DEFAULT = SystemResources(
      availableMemoryPercent = 50,
      cpuUsagePercent = 50
    )
  }
}

/**
 * Represents a tag's identifying information
 */
data class TagInfo(
  val crc32: Long,
  val timestamp: Int,
  val type: FlvTagHeaderType,
  val size: Int,
)

/**
 * Enhanced duplicate detection system with adaptive thresholds and pattern recognition
 */
internal class DuplicateDetector(
  private val context: StreamerContext,
  private var config: DuplicateConfig,
) {
  private val tagHistory = LinkedList<TagInfo>()
  private val patternDetector = DuplicatePatternDetector()
  private val stats = DuplicateStats()
  private var consecutiveDuplicates = 0
  private var totalTags = 0
  private var totalDuplicates = 0

  init {
    patternDetector.name = context.name
  }

  fun isDuplicate(tag: TagInfo): Boolean {
    totalTags++

    // Update statistics
    stats.analyze(tag)

    // Check for patterns first
    if (patternDetector.detectPattern(tag)) {
      logger.debug("${context.name} Pattern-based duplicate detected")
      incrementDuplicateCount()
      return true
    }

    // Check against history with weighted comparison
    val isDuplicate = tagHistory.any { it.isSimilarTo(tag, config) }

    if (isDuplicate) {
      incrementDuplicateCount()
      if (consecutiveDuplicates >= config.maxConsecutiveDuplicates) {
        handleExcessiveDuplicates()
      }
    } else {
      consecutiveDuplicates = 0
      updateHistory(tag)
    }

    // Periodically adjust configuration
    if (totalTags % 1000 == 0) {
      adjustConfiguration()
    }

    return isDuplicate
  }

  private fun incrementDuplicateCount() {
    consecutiveDuplicates++
    totalDuplicates++

    // Update network condition based on duplicate rate
    if (totalTags > 100) {
      val duplicateRate = totalDuplicates.toDouble() / totalTags
      updateNetworkCondition(context.name, duplicateRate, config)
    }
  }

  private fun updateHistory(tag: TagInfo) {
    tagHistory.add(tag)
    while (tagHistory.size > config.historySize) {
      tagHistory.removeFirst()
    }
  }

  private fun handleExcessiveDuplicates() {
    logger.warn(
      """
            ${context.name} Excessive duplicates detected:
            - Consecutive: $consecutiveDuplicates
            - Total rate: ${totalDuplicates * 100.0 / totalTags}%
            - Network condition: ${config.networkCondition}
            - Current thresholds: ${config.currentThresholds}
        """.trimIndent()
    )

    // Adjust thresholds for poor conditions
    config = config.withDegradedConditions()
  }

  private fun adjustConfiguration() {
    val duplicateRate = totalDuplicates.toDouble() / totalTags

    config = config.copy(
      timeWindow = stats.calculateOptimalTimeWindow(),
      similarityThreshold = stats.calculateOptimalThreshold(duplicateRate),
      networkCondition = determineNetworkCondition(duplicateRate)
    )

    logger.trace(
      """
            ${context.name} Configuration adjusted:
            - Duplicate rate: ${duplicateRate * 100}%
            - Time window: ${config.timeWindow}ms
            - Similarity threshold: ${config.similarityThreshold}
            - Network condition: ${config.networkCondition}
        """.trimIndent()
    )
  }
}

/**
 * Pattern detection for repeating duplicate sequences
 */
private class DuplicatePatternDetector {
  var name: String = ""
  private val recentTags = LinkedList<TagInfo>()
  private val patterns = mutableMapOf<String, PatternInfo>()

  data class PatternInfo(
    var count: Int = 0,
    var lastSeen: Long = System.currentTimeMillis(),
    var confidence: Double = 0.0,
    var avgTimeDiff: Double = 0.0,
    var timeDiffVariance: Double = 0.0,
  )

  fun detectPattern(tag: TagInfo): Boolean {
    // Don't detect patterns until we have enough history
    if (recentTags.size < PATTERN_WINDOW - 1) {
      logger.debug("$name Building pattern history: ${recentTags.size + 1}/$PATTERN_WINDOW")
      recentTags.add(tag)
      return false
    }

    // Build pattern signature
    val pattern = buildPatternSignature(recentTags, tag)
//    logger.debug("Generated pattern signature: $pattern")

    // Update pattern statistics
    val info = patterns.getOrPut(pattern) { PatternInfo() }
    updatePatternStats(info, recentTags, tag)

//    logger.debug("""
//        Pattern stats:
//        - Signature: $pattern
//        - Count: ${info.count}
//        - Avg Time Diff: ${info.avgTimeDiff}ms
//        - Time Variance: ${info.timeDiffVariance}
//        - Confidence: ${info.confidence}
//    """.trimIndent())

    // Update recent tags
    recentTags.add(tag)
    if (recentTags.size > PATTERN_WINDOW) {
      recentTags.removeFirst()
    }

    // Clean old patterns periodically
    cleanOldPatterns()

    // Check if this is a duplicate based on pattern
    return isPatternDuplicate(info, tag)
  }

  private fun buildPatternSignature(history: List<TagInfo>, current: TagInfo): String {
    return buildString {
      // Create a sequence of tag types using type identifiers
      history.forEach { tag ->
        append(getTypeIdentifier(tag.type))
      }
      append(getTypeIdentifier(current.type))

      // Add size ranges
      append("-S")
      history.forEach { tag ->
        append(getSizeCategory(tag.size))
      }
      append(getSizeCategory(current.size))
    }
  }

  private fun getTypeIdentifier(type: FlvTagHeaderType): Char = when (type) {
    FlvTagHeaderType.Audio -> 'A'
    FlvTagHeaderType.Video -> 'V'
    FlvTagHeaderType.ScriptData -> 'S'
    else -> 'U'
  }

  private fun getSizeCategory(size: Int): Char = when {
    size < 100 -> 'T'      // Tiny
    size < 1000 -> 'S'     // Small
    size < 10000 -> 'M'    // Medium
    size < 100000 -> 'L'   // Large
    else -> 'H'            // Huge
  }

  private fun updatePatternStats(info: PatternInfo, history: List<TagInfo>, current: TagInfo) {
    info.count++
    info.lastSeen = System.currentTimeMillis()

    // Calculate time differences
    val timeDiffs = mutableListOf<Int>()
    var lastTimestamp = history.first().timestamp
    history.drop(1).forEach { tag ->
      timeDiffs.add(tag.timestamp - lastTimestamp)
      lastTimestamp = tag.timestamp
    }
    timeDiffs.add(current.timestamp - lastTimestamp)

    // Update running average and variance
    val newAvg = timeDiffs.average()
    val newVariance = timeDiffs.map { (it - newAvg).pow(2) }.average()

    // Exponential moving average for smooth updates
    info.avgTimeDiff = if (info.count == 1) newAvg
    else 0.8 * info.avgTimeDiff + 0.2 * newAvg
    info.timeDiffVariance = if (info.count == 1) newVariance
    else 0.8 * info.timeDiffVariance + 0.2 * newVariance

    // Calculate confidence based on multiple factors
    info.confidence = calculateConfidence(info, timeDiffs)
  }

  private fun calculateConfidence(info: PatternInfo, currentTimeDiffs: List<Int>): Double {
    val timeFactor = (System.currentTimeMillis() - info.lastSeen) / 1000.0
    val ageFactor = exp(-timeFactor / PATTERN_AGE_FACTOR)

    // Count factor: how many times we've seen this pattern
    val countFactor = (info.count.toDouble() / MIN_PATTERN_COUNT).coerceAtMost(1.0)

    // Consistency factor: how stable are the time differences
    val consistencyFactor = if (info.timeDiffVariance > 0) {
      val cv = sqrt(info.timeDiffVariance) / info.avgTimeDiff // Coefficient of variation
      exp(-cv).coerceIn(0.0, 1.0)
    } else 1.0

    // Current match factor: how well current timing matches the pattern
    val matchFactor = currentTimeDiffs.map { timeDiff ->
      val deviation = abs(timeDiff - info.avgTimeDiff)
      exp(-deviation / info.avgTimeDiff).coerceIn(0.0, 1.0)
    }.average()

    return (countFactor * ageFactor * consistencyFactor * matchFactor).coerceIn(0.0, 1.0)
  }

  private fun isPatternDuplicate(info: PatternInfo, tag: TagInfo): Boolean {
    val meetsBasicCriteria = info.count > MIN_PATTERN_COUNT &&
            info.confidence > PATTERN_CONFIDENCE_THRESHOLD

    if (!meetsBasicCriteria) return false

    // Additional validation for timing
    val lastTimeDiff = tag.timestamp - recentTags.last().timestamp
    val withinTimeWindow = abs(lastTimeDiff - info.avgTimeDiff) <=
            sqrt(info.timeDiffVariance) * 3 // 3-sigma rule

    val isDuplicate = meetsBasicCriteria && withinTimeWindow

    if (isDuplicate) {
      logger.debug(
        """
          Pattern-based duplicate detected:
          - Pattern count: ${info.count}
          - Confidence: ${info.confidence}
          - Expected time diff: ${info.avgTimeDiff}±${sqrt(info.timeDiffVariance)}ms
          - Actual time diff: ${lastTimeDiff}ms
      """.trimIndent()
      )
    }

    return isDuplicate
  }

  private fun cleanOldPatterns() {
    val now = System.currentTimeMillis()
    val removedPatterns = mutableListOf<String>()
    patterns.entries.removeIf { (pattern, info) ->
      val shouldRemove = now - info.lastSeen > PATTERN_EXPIRY_TIME || info.confidence < MIN_CONFIDENCE
      if (shouldRemove) {
        removedPatterns.add("$pattern (age=${(now - info.lastSeen) / 1000}s, confidence=${info.confidence})")
      }
      shouldRemove
    }
    if (removedPatterns.isNotEmpty()) {
      logger.trace("Removed patterns:\n${removedPatterns.joinToString("\n")}")
    }
  }

  companion object {
    private const val PATTERN_WINDOW = 8
    private const val MIN_PATTERN_COUNT = 5
    private const val PATTERN_CONFIDENCE_THRESHOLD = 0.92
    private const val PATTERN_AGE_FACTOR = 30.0
    private const val MIN_CONFIDENCE = 0.4
    private const val PATTERN_EXPIRY_TIME = 60_000L
  }
}

/**
 * Circular buffer for efficient statistical calculations
 */
private class CircularBuffer<T : Number>(private val maxSize: Int) : Iterable<T> {
  private val buffer = ArrayDeque<T>(maxSize)

  fun add(item: T) {
    if (buffer.size >= maxSize) {
      buffer.removeFirst()
    }
    buffer.addLast(item)
  }

  override fun iterator(): Iterator<T> = buffer.iterator()

  fun isNotEmpty() = buffer.isNotEmpty()
  fun last(): T = buffer.last()

  fun average(): Double = buffer.map { it.toDouble() }.average()

  fun standardDeviation(): Double {
    val avg = average()
    return sqrt(buffer.map { (it.toDouble() - avg).pow(2) }.average())
  }
}

/**
 * Weighted comparison for tag similarity
 */
fun TagInfo.isSimilarTo(other: TagInfo, config: DuplicateConfig): Boolean {
  var score = 0.0

  // CRC32 match (40% weight)
  if (this.crc32 == other.crc32) {
    score += 0.4
  }

  // Type match (20% weight)
  if (this.type == other.type) {
    score += 0.2
  }

  // Size match (20% weight)
  if (this.size == other.size) {
    score += 0.2
  }

  // Timestamp proximity (20% weight)
  val timeDiff = abs(this.timestamp - other.timestamp)
  if (timeDiff <= config.maxTimeDifference) {
    // Score decreases as time difference increases
    score += 0.2 * (1.0 - timeDiff.toDouble() / config.maxTimeDifference)
  }

  return score >= config.similarityThreshold
}

private fun determineNetworkCondition(duplicateRate: Double): NetworkCondition = when {
  duplicateRate > 0.8 -> NetworkCondition.POOR
  duplicateRate > 0.4 -> NetworkCondition.MODERATE
  else -> NetworkCondition.GOOD
}

private fun updateNetworkCondition(name: String, duplicateRate: Double, config: DuplicateConfig) {
  // Update network condition based on duplicate rate
  val newCondition = determineNetworkCondition(duplicateRate)
  if (newCondition != config.networkCondition) {
    logger.info("$name Network condition changed from ${config.networkCondition} to $newCondition (duplicate rate: ${duplicateRate * 100}%)")
  }
}

/**
 * Enhanced duplicate elimination rule with adaptive configuration
 */
internal fun Flow<FlvData>.removeDuplicates(
  context: StreamerContext,
  enable: Boolean = true,
  quality: StreamQuality = StreamQuality.LOW,
  networkCondition: NetworkCondition = NetworkCondition.MODERATE,
  systemResources: SystemResources = SystemResources.DEFAULT,
): Flow<FlvData> = if (!enable) this else flow {
  val config = DuplicateConfig(
    networkCondition = networkCondition,
    quality = quality,
    systemResources = systemResources
  )

  val detector = DuplicateDetector(context, config)

  logger.debug(
    "{} Duplicate detection config: maxConsecutive={}, maxTimeDiff={}, historySize={}, similarity={}",
    context.name,
    config.maxConsecutiveDuplicates,
    config.maxTimeDifference,
    config.historySize,
    config.similarityThreshold
  )

  collect { flvData ->
    val tagInfo = flvData.toTagInfo()

    if (tagInfo == null || !detector.isDuplicate(tagInfo)) {
      emit(flvData)
    } else {
      logger.debug("{} Skipping duplicate tag: {}", context.name, flvData)
    }
  }
}

/**
 * Creates TagInfo from FlvData
 */
fun FlvData.toTagInfo(): TagInfo? = when (this) {
  is FlvTag -> TagInfo(
    crc32 = this.crc32,
    timestamp = this.header.timestamp,
    type = this.header.tagType,
    size = this.header.dataSize
  )

  else -> null
}

/**
 * Statistical analysis for duplicate detection
 */
private class DuplicateStats {
  private val timestampDeltas = CircularBuffer<Int>(100)
  private val sizeDistribution = mutableMapOf<Int, Int>()
  private val typeDistribution = mutableMapOf<FlvTagHeaderType, Int>()

  fun analyze(tag: TagInfo) {
    // Update timestamp deltas
    if (timestampDeltas.isNotEmpty()) {
      val delta = tag.timestamp - timestampDeltas.last()
      timestampDeltas.add(delta)
    } else {
      timestampDeltas.add(tag.timestamp)
    }

    // Update size distribution
    sizeDistribution[tag.size] = (sizeDistribution[tag.size] ?: 0) + 1

    // Update type distribution
    typeDistribution[tag.type] = (typeDistribution[tag.type] ?: 0) + 1
  }

  fun calculateOptimalTimeWindow(): Long {
    if (timestampDeltas.isNotEmpty()) {
      val avgDelta = timestampDeltas.average()
      val stdDev = timestampDeltas.standardDeviation()

      // Use 3-sigma rule for window size
      return (avgDelta + 3 * stdDev).toLong().coerceIn(1000L, 10000L)
    }
    return 5000L // Default window if no data available
  }

  fun calculateOptimalThreshold(duplicateRate: Double): Double {
    // Adjust threshold based on duplicate rate and type distribution
    val threshold = when {
      duplicateRate > 0.8 -> 0.9  // Strict matching for high duplicate rates
      duplicateRate > 0.5 -> 0.8  // Moderate matching
      else -> 0.7                 // Relaxed matching for low duplicate rates
    }

    // Further adjust based on tag type distribution
    val dominantType = typeDistribution.maxByOrNull { it.value }?.key
    return when (dominantType) {
      FlvTagHeaderType.Video -> threshold * 1.1  // Stricter for video
      FlvTagHeaderType.Audio -> threshold * 0.9  // More lenient for audio
      else -> threshold
    }.coerceIn(0.6, 0.95)
  }
}
