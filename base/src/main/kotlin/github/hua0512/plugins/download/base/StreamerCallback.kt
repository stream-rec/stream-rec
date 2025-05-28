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

package github.hua0512.plugins.download.base

import github.hua0512.data.stream.StreamData
import github.hua0512.data.stream.Streamer
import github.hua0512.data.stream.StreamerState
import github.hua0512.flv.data.other.FlvMetadataInfo

/**
 * Interface for listening to streamer events
 * @see Streamer
 * @author hua0512
 * @date : 2024/5/25 10:05
 */
interface StreamerCallback {

  /**
   * Called when the state of a streamer changes.
   * @param streamerId The ID of the streamer.
   * @param streamerUrl The URL of the streamer.
   * @param newState The new state of the streamer.
   * @param message Optional message associated with the state change.
   * @param onSuccessful Callback to be invoked on successful state change handling.
   */
  suspend fun onStateChanged(
    streamerId: Long,
    streamerUrl: String,
    newState: StreamerState,
    message: String?,
    onSuccessful: () -> Unit
  )

  /**
   * Called when the last live time of a streamer changes.
   * @param streamerId The ID of the streamer.
   * @param streamerUrl The URL of the streamer.
   * @param newLiveTime The new live time in milliseconds.
   * @param onSuccessful Callback to be invoked on successful live time change handling.
   */
  suspend fun onLastLiveTimeChanged(streamerId: Long, streamerUrl: String, newLiveTime: Long, onSuccessful: () -> Unit)

  /**
   * Called when the description of a streamer changes.
   * @param streamerId The ID of the streamer.
   * @param streamerUrl The URL of the streamer.
   * @param description The new description of the streamer.
   * @param onSuccessful Callback to be invoked on successful description change handling.
   */
  suspend fun onDescriptionChanged(streamerId: Long, streamerUrl: String, description: String, onSuccessful: () -> Unit)

  /**
   * Called when the avatar of a streamer changes.
   * @param streamerId The ID of the streamer.
   * @param streamerUrl The URL of the streamer.
   * @param avatar The new avatar URL.
   * @param onSuccessful Callback to be invoked on successful avatar change handling.
   */
  suspend fun onAvatarChanged(streamerId: Long, streamerUrl: String, avatar: String, onSuccessful: () -> Unit)

  /**
   * Called when a stream is downloaded successfully.
   * @param streamerId The ID of the streamer.
   * @param streamerUrl The URL of the streamer.
   * @param streamData The data of the downloaded stream.
   * @param shouldInjectMetaInfo Whether to inject metadata info into the stream.
   * @param metaInfo Optional metadata info to be injected into the stream.
   */
  fun onStreamDownloaded(
    streamerId: Long,
    streamerUrl: String,
    streamData: StreamData,
    shouldInjectMetaInfo: Boolean = false,
    metaInfo: FlvMetadataInfo? = null
  )

  /**
   * Called when a stream download fails.
   * @param streamerId The ID of the streamer.
   * @param streamerUrl The URL of the streamer.
   * @param stream The stream data that failed to download.
   * @param exception The exception that occurred during the download.
   */
  fun onStreamDownloadFailed(streamerId: Long, streamerUrl: String, stream: StreamData?, exception: Exception)

  /**
   * Called when a stream is terminated.
   * @param streamerId The ID of the streamer.
   * @param streamerUrl The URL of the streamer.
   * @param streams The list of streams that have been downloaded.
   */
  fun onStreamFinished(streamerId: Long, streamerUrl: String, streams: List<StreamData>)
}