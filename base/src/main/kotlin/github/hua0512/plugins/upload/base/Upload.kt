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

package github.hua0512.plugins.upload.base

import github.hua0512.app.App
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadResult
import github.hua0512.plugins.upload.exceptions.UploadFailedException
import github.hua0512.plugins.upload.exceptions.UploadInvalidArgumentsException

/**
 * Base class for uploaders.
 * @param T The upload configuration type.
 * @property app The application instance.
 * @property config The upload configuration.
 */
abstract class Upload<T : UploadConfig>(protected val app: App, open val config: T?) {

  /**
   * Uploads the given data.
   * @param uploadData The data to upload.
   * @return The upload result.
   *
   * @throws UploadInvalidArgumentsException If the upload data is invalid.
   * @throws UploadFailedException If the upload failed.
   */
  suspend fun upload(uploadData: UploadData): UploadResult {
    if (uploadData.streamData == null) {
      throw UploadInvalidArgumentsException("stream data not initialized : $uploadData")
    }

    if (uploadData.streamData.streamer == null) {
      throw UploadInvalidArgumentsException("streamer not initialized : $uploadData")
    }
    return performUpload(uploadData)
  }

  /**
   * Performs the upload.
   * @param uploadData The data to upload.
   * @return The upload result.
   *
   *@throws UploadInvalidArgumentsException If the upload data is invalid.
   *@throws UploadFailedException If the upload failed.
   */
  internal abstract suspend fun performUpload(uploadData: UploadData): UploadResult
}