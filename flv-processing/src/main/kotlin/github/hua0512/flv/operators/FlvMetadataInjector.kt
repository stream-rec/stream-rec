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
import github.hua0512.flv.data.amf.Amf0Keyframes
import github.hua0512.flv.data.amf.Amf0Value
import github.hua0512.flv.data.other.FlvMetadataInfo
import github.hua0512.flv.data.sound.FlvSoundType
import github.hua0512.flv.exceptions.FlvDataErrorException
import github.hua0512.flv.naturalMetadataKeyOrder
import github.hua0512.flv.utils.ScriptData
import github.hua0512.flv.utils.createMetadataTag
import github.hua0512.flv.utils.isTrueScripTag
import github.hua0512.flv.utils.logger
import io.exoquery.pprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.collections.plus


private const val TAG = "FlvMetadataInjectorRule"
private val logger by lazy { logger(TAG) }


/**
 * Injects metadata into script tags.
 * @author hua0512
 * @date : 2024/9/7 16:13
 */
internal fun Flow<FlvData>.injectMetadata(): Flow<FlvData> = flow {

  val extraData = FlvMetadataInfo().toAmfMap()

  fun FlvTag.injectMetadata(): FlvTag {
    var tagData = data as ScriptData
    val obj = tagData[1] // This is the second AMF value in the metadata

    val oldSize = header.dataSize
//    logger.debug("Injecting to: {}, oldSize={}, calculatedSize={}", pprint(obj), oldSize, tagData.bodySize)

    // Inject a new AMF value
    when (obj) {
      is Amf0Value.EcmaArray -> {
        val newProperties = obj.properties + extraData
        val newObj = obj.copy(properties = newProperties.sortKeys())
        tagData = tagData.copy(values = listOf(tagData[0], newObj))

      }

      is Amf0Value.Object -> {
        val newProperties = obj.properties + extraData
        val newObj = Amf0Value.Object(newProperties.sortKeys())
        tagData = tagData.copy(values = listOf(tagData[0], newObj))
      }

      else -> throw FlvDataErrorException("Unexpected AMF value type in metadata : ${obj::class.qualifiedName}")
    }

    logger.debug("Injected metadata: {}", pprint(tagData[1], defaultHeight = 50))

    // recompute the script tag size
    val newSize = tagData.bodySize
    logger.debug("Script tag size changed from $oldSize to $newSize")
    return this.copy(header = header.copy(dataSize = newSize), data = tagData)
  }

  collect { data ->
    if (data is FlvTag && data.num == 1) {
      if (data.isTrueScripTag()) {
        val newTag = data.injectMetadata()
        emit(newTag)
        return@collect
      } else {
        logger.debug("Script tag not found...")
        val scriptTag = createMetadataTag(1, data.header.timestamp, data.header.streamId)
        logger.debug("Created metadata tag: {}", pprint(scriptTag))
        val newTag = scriptTag.injectMetadata()
        emit(newTag)
      }
    }
    emit(data)
  }
}


/**
 * Sorts the keys of a map of AMF0 values in natural metadata key order.
 * @return a map of AMF0 values with sorted keys
 * @see naturalMetadataKeyOrder
 * @see Amf0Value
 */
internal fun Map<String, Amf0Value>.sortKeys(): Map<String, Amf0Value> {
  val sortedKeys = naturalMetadataKeyOrder + (this.keys - naturalMetadataKeyOrder)
  @Suppress("UNCHECKED_CAST")
  return sortedKeys.associateWith { this[it] }.filterValues { it != null } as Map<String, Amf0Value>
}

/**
 * Converts [FlvMetadataInfo] to a map of AMF0 values.
 * @return a map of AMF0 values
 * @see Amf0Value
 */
internal fun FlvMetadataInfo.toAmfMap(): Map<String, Amf0Value> {
  val streamRec = Amf0Value.String("Stream-rec")
  val amf0Keyframes = Amf0Keyframes(ArrayList()).apply {
    if (keyframes.isNotEmpty()) {
      try {
        addKeyframes(keyframes)
      } catch (_: IllegalArgumentException) {
        logger.warn("Maximum keyframes size exceeded, truncating to ${Amf0Keyframes.MAX_KEYFRAMES_PERMITTED}")
      }
      logger.debug("keyframes : {}", keyframesCount)
    }
  }

  return mapOf(
    "hasAudio" to Amf0Value.Boolean(hasAudio),
    "hasVideo" to Amf0Value.Boolean(hasVideo),
    "hasMetadata" to Amf0Value.Boolean(hasScript),
    "hasKeyframes" to Amf0Value.Boolean(hasKeyframes),
    "canSeekToEnd" to Amf0Value.Boolean(canSeekToEnd),
    "duration" to Amf0Value.Number(duration.toDouble()),
    "filesize" to Amf0Value.Number(fileSize.toDouble()),
    "metadatacreator" to streamRec,
    "width" to Amf0Value.Number(width.toDouble()),
    "height" to Amf0Value.Number(height.toDouble()),
    "keyframes" to amf0Keyframes,
    "stereo" to Amf0Value.Boolean(audioSoundType == FlvSoundType.STEREO),
    "lasttimestamp" to Amf0Value.Number(lastTimestamp.toDouble()),
    "lastkeyframetimestamp" to Amf0Value.Number(keyframes.lastOrNull()?.timestamp?.toDouble() ?: 0.0),
    "lastkeyframelocation" to Amf0Value.Number(keyframes.lastOrNull()?.filePosition?.toDouble() ?: 0.0),
    "recordTool" to streamRec
  )
}