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

package github.hua0512.repo

import github.hua0512.dao.stats.StatsDao
import github.hua0512.dao.upload.UploadActionDao
import github.hua0512.dao.upload.UploadActionFilesDao
import github.hua0512.dao.upload.UploadDataDao
import github.hua0512.dao.upload.UploadResultDao
import github.hua0512.data.*
import github.hua0512.data.upload.*
import github.hua0512.logger
import github.hua0512.repo.streamer.StreamDataRepo
import github.hua0512.repo.uploads.UploadRepo
import github.hua0512.utils.StatsEntity
import github.hua0512.utils.UploadDataEntity
import github.hua0512.utils.getTodayStart
import github.hua0512.utils.withIOContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Repository class for managing upload actions.
 * This class provides methods for streaming failed upload results, saving upload actions and results,
 * getting upload data, changing upload data status, deleting upload results, and getting upload action by upload data ID.
 *
 * @property json Json serializer/deserializer.
 * @property uploadActionDao DAO for managing upload actions.
 * @property uploadDataDao DAO for managing upload data.
 * @property uploadActionFilesDao DAO for managing upload action files.
 * @property uploadResultDao DAO for managing upload results.
 *
 * @author hua0512
 * @date : 2024/2/17 20:20
 */
class UploadActionRepository(
  val json: Json,
  val streamsRepo: StreamDataRepo,
  val uploadActionDao: UploadActionDao,
  val uploadDataDao: UploadDataDao,
  val uploadActionFilesDao: UploadActionFilesDao,
  val uploadResultDao: UploadResultDao,
  val statsDao: StatsDao,
) : UploadRepo {

  /**
   * Streams all failed upload results.
   *
   * This function retrieves all failed upload results from the database and converts them to UploadResult objects.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @return Flow of list of UploadResult
   */
  override suspend fun streamFailedUploadResults(): Flow<List<UploadResult>> =
    uploadResultDao.streamAllFailedUploadResult().map {
      it.map { result ->
        UploadResult(result).apply {
          populateUploadData(UploadDataId(result.uploadDataId))
        }
      }
    }.flowOn(Dispatchers.IO)

  override suspend fun getAllUploadData(): List<UploadData> {
    return withIOContext {
      uploadDataDao.getAllUploadData().mapToUploadData()
    }
  }

  override suspend fun getAllUploadDataPaginated(
    page: Int,
    pageSize: Int,
    status: List<Long>?,
    filter: String?,
    streamers: List<StreamerId>?,
    sortColumn: String?,
    sortOrder: String?,
  ): List<UploadData> {
    val sortColumn = sortColumn ?: "id"
    val sortOrder = sortOrder ?: "DESC"

    return withIOContext {
      uploadDataDao.getAllUploadDataPaginated(
        page,
        pageSize,
        filter ?: "",
        status = status ?: UploadState.entries.map { it.value.toLong() },
        streamers,
        streamers?.isEmpty() ?: true,
        sortColumn,
        sortOrder
      )
        .mapToUploadData()
    }
  }

  override suspend fun countAllUploadData(status: List<Long>?, filter: String?, streamerId: Collection<StreamerId>?): Long {
    return withIOContext {
      uploadDataDao.countAllUploadData(
        status ?: UploadState.entries.map { it.value.toLong() },
        filter ?: "",
        streamerId
      )
    }
  }

  private suspend fun List<UploadDataEntity>.mapToUploadData() = this.map { uploadData ->
    UploadData(
      id = uploadData.id,
      filePath = uploadData.filePath,
      status = UploadState.fromValue(uploadData.status.toInt())
    ).apply {
      streamDataId = uploadData.streamDataId ?: -1
      uploadAction = getUploadActionIdByUploadDataId(UploadDataId(uploadData.id))
        ?: throw IllegalStateException("Upload action not found for upload data: $this")
      streamData = streamsRepo.getStreamDataById(StreamDataId(uploadData.streamDataId!!))
        ?: throw IllegalStateException("Stream data not found for upload data: $this")
    }
  }

  /**
   * Retrieves all upload results.
   * This function retrieves all upload results from the database and converts them to UploadResult objects.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @return List of UploadResult
   * @throws IllegalStateException if upload data is not found for a result
   */
  override suspend fun getAllUploadResults(): List<UploadResult> {
    return withIOContext {
      uploadResultDao.getAllUploadResults().map { result ->
        UploadResult(result).apply {
          populateUploadData(UploadDataId(result.uploadDataId))
        }
      }
    }
  }

  override suspend fun getUploadAction(id: UploadActionId): UploadAction? {
    return withIOContext {
      uploadActionDao.getUploadActionById(id)?.let { uploadAction ->
        UploadAction(
          id = uploadAction.id,
          time = uploadAction.time,
          uploadConfig = json.decodeFromString(UploadConfig.serializer(), uploadAction.uploadConfig)
        )
      }
    }
  }


  /**
   * Saves an upload action.
   *
   * This function saves an upload action to the database. It also saves the associated upload data and inserts the upload action files.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param uploadAction The upload action to save
   * @return The ID of the saved upload action
   */
  override suspend fun saveAction(uploadAction: UploadAction): UploadActionId {
    val actionId = withIOContext {
      val uploadConfigString = json.encodeToString(UploadConfig.serializer(), uploadAction.uploadConfig)
      uploadActionDao.saveUploadAction(uploadAction.time, uploadConfigString)
    }
    // save upload data
    withIOContext {
      uploadAction.files.forEach {
        logger.debug("Saving upload data for file: {}, {}", it, it.streamDataId)
        val uploadDataId = uploadDataDao.insertUploadData(
          it.filePath,
          StreamDataId(it.streamDataId),
          it.status.value.toLong()
        )
        it.id = uploadDataId
        it.uploadAction = uploadAction
        // insert into upload action files
        uploadActionFilesDao.insertUploadActionFiles(actionId, UploadDataId(uploadDataId))
      }
    }
    return actionId
  }

  /**
   * Saves an upload result.
   *
   * This function saves an upload result to the database.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param uploadResult The upload result to save
   */
  override suspend fun saveResult(uploadResult: UploadResult) {
    return withIOContext {
      uploadResult.id = uploadResultDao.saveUploadResult(uploadResult.toEntity())
      val today = getTodayStart().epochSeconds
      val todayStats = statsDao.getStatsFromTo(today, today + 86400000).firstOrNull()

      if (todayStats == null) {
        if (uploadResult.isSuccess) {
          statsDao.insertStats(StatsEntity(0, today, 0, 1, 0))
        } else {
          statsDao.insertStats(StatsEntity(0, today, 0, 0, 1))
        }
      } else {
        if (uploadResult.isSuccess) {
          val totalUploads = todayStats.totalUploads + 1
          statsDao.updateStats(todayStats.copy(totalUploads = totalUploads))
        } else {
          val totalFailedUploads = todayStats.totalFailedUploads + 1
          statsDao.updateStats(todayStats.copy(totalFailedUploads = totalFailedUploads))
        }
      }
    }
  }

  /**
   * Retrieves upload data by its ID.
   *
   * This function retrieves upload data from the database using the upload data ID.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param uploadDataId The ID of the upload data
   * @return UploadData or null if no upload data with the given ID exists
   */
  override suspend fun getUploadData(uploadDataId: UploadDataId): UploadData? {
    return withIOContext {
      uploadDataDao.getUploadDataById(uploadDataId)?.let { uploadData ->
        UploadData(
          id = uploadData.id,
          filePath = uploadData.filePath,
          status = UploadState.fromValue(uploadData.status.toInt())
        ).also {
          it.uploadAction =
            getUploadActionIdByUploadDataId(uploadDataId) ?: throw IllegalStateException("Upload action not found for upload data: $this")
          it.streamData = streamsRepo.getStreamDataById(StreamDataId(uploadData.streamDataId!!))
            ?: throw IllegalStateException("Stream data not found for upload data: $this")
        }
      }
    }
  }

  /**
   * Changes the status of the upload data.
   *
   * This function updates the status of the upload data in the database.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param uploadDataId The ID of the upload data to update.
   * @param status The new status to set for the upload data.
   */
  override suspend fun changeUploadDataStatus(uploadDataId: Long, status: UploadState) {
    return withIOContext {
      uploadDataDao.updateUploadDataStatus(UploadDataId(uploadDataId), status.value.toLong())
    }
  }

  override suspend fun deleteUploadData(id: UploadDataId) {
    return withIOContext {
      uploadDataDao.deleteUploadData(id)
    }
  }

  /**
   * Deletes an upload result.
   *
   * This function deletes an upload result from the database.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param id The ID of the upload result to delete.
   */
  override suspend fun deleteUploadResult(id: UploadResultId) {
    return withIOContext {
      uploadResultDao.deleteUploadResult(id)
    }
  }

  /**
   * Retrieves an upload action by its associated upload data ID.
   *
   * This function retrieves an upload action from the database using the upload data ID.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param id The ID of the upload data.
   * @return UploadAction or null if no upload action with the given upload data ID exists.
   */
  override suspend fun getUploadActionIdByUploadDataId(id: UploadDataId): UploadAction? {
    return withIOContext {
      uploadActionFilesDao.getUploadActionByUploadDataId(id)?.let { uploadAction ->
        UploadAction(
          id = uploadAction.id,
          time = uploadAction.time,
          uploadConfig = json.decodeFromString(UploadConfig.serializer(), uploadAction.uploadConfig)
        )
      }
    }
  }

  override suspend fun getUploadDataResults(uploadDataId: UploadDataId): List<UploadResult> {
    return withIOContext {
      uploadResultDao.findUploadResultByUploadId(uploadDataId).map { result ->
        UploadResult(result).apply {
          populateUploadData(uploadDataId)
        }
      }
    }
  }

  /**
   * Retrieves upload data for a given upload result.
   * This function retrieves upload data from the database using the upload data ID associated with the upload result.
   * @return UploadData or null if no upload data with the given ID exists
   * @throws IllegalStateException if upload data is not found for the result
   */
  private suspend fun UploadResult.populateUploadData(id: UploadDataId) {
    uploadData = getUploadData(id) ?: throw IllegalStateException("Upload data not found for result: $this")
  }

}
