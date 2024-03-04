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

package github.hua0512.dao.upload

import github.hua0512.data.UploadActionId
import github.hua0512.utils.UploadActionEntity

/**
 * Interface for managing upload actions.
 * Provides methods for retrieving, saving, and deleting upload actions.
 *
 * @author hua0512
 * @date : 2024/2/19 10:52
 */
interface UploadActionDao {

  /**
   * Retrieves an upload action by its ID.
   *
   * @param uploadId The ID of the upload action
   * @return UploadActionEntity or null if no upload action with the given ID exists
   */
  fun getUploadActionById(uploadId: UploadActionId): UploadActionEntity?

  /**
   * Retrieves all upload actions associated with a specific upload ID.
   *
   * @param uploadId The ID of the upload
   * @return List of UploadActionEntity
   */
  fun getUploadActionByUploadId(uploadId: UploadActionId): List<UploadActionEntity>

  /**
   * Saves an upload action.
   *
   * @param time The time of the upload action
   * @param configString The configuration string of the upload action
   * @return The ID of the saved upload action
   */
  fun saveUploadAction(time: Long, configString: String): UploadActionId

  /**
   * Deletes an upload action.
   *
   * @param uploadActionId The ID of the upload action to delete
   */
  fun deleteUploadAction(uploadActionId: UploadActionId)
}