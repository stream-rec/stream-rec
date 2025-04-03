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
import github.hua0512.dao.upload.UploadDataDao
import github.hua0512.dao.upload.UploadResultDao
import github.hua0512.data.StreamDataId
import github.hua0512.data.StreamerId
import github.hua0512.data.UploadDataId
import github.hua0512.data.stats.StatsEntity
import github.hua0512.data.stream.StreamData
import github.hua0512.data.upload.UploadData
import github.hua0512.data.upload.UploadDataWithStream
import github.hua0512.data.upload.UploadResult
import github.hua0512.data.upload.UploadState
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.repo.stream.StreamerRepo
import github.hua0512.repo.upload.UploadRepo
import github.hua0512.utils.withIOContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Repository class for managing upload related actions.
 *
 * @property streamerRepo Repository for managing streamers.
 * @property streamsRepo Repository for managing stream data.
 * @property uploadDataDao DAO for managing upload data.
 * @property uploadResultDao DAO for managing upload results.
 *
 * @author hua0512
 * @date : 2024/2/17 20:20
 */
class UploadActionRepository(
  private val streamerRepo: StreamerRepo,
  private val streamsRepo: StreamDataRepo,
  private val uploadDataDao: UploadDataDao,
  private val uploadResultDao: UploadResultDao,
  private val statsDao: StatsDao,
) : UploadRepo {

  private companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(UploadActionRepository::class.java)
  }

  override suspend fun getAllUploadData(): List<UploadData> {
    return withIOContext {
      uploadDataDao.getAllWithStreamAndAction()
        .map {
          it.toUploadData()
        }
    }
  }


  override suspend fun getAllUploadDataPaginated(
    page: Int,
    pageSize: Int,
    status: List<Int>?,
    filter: String?,
    streamers: List<StreamerId>?,
    sortColumn: String?,
    sortOrder: String?,
  ): List<UploadData> {
    val sortColumn = sortColumn ?: "id"
    val sortOrder = sortOrder ?: "DESC"

    return withIOContext {
      if (sortOrder != "ASC" && sortOrder != "DESC") {
        throw IllegalArgumentException("Invalid sort order: $sortOrder")
      }

      return@withIOContext when (sortOrder) {
        "ASC" -> {
          uploadDataDao.getAllFilteredPaginatedAsc(
            (page - 1) * pageSize,
            pageSize,
            filter ?: "",
            status = status ?: UploadState.intValues(),
            streamers?.map { it.value } ?: emptyList(),
            streamers?.isEmpty() != false,
            sortColumn
          )
        }

        "DESC" -> {
          uploadDataDao.getAllFilteredPaginatedDesc(
            (page - 1) * pageSize,
            pageSize,
            filter ?: "",
            status = status ?: UploadState.intValues(),
            streamers?.map { it.value } ?: emptyList(),
            streamers?.isEmpty() != false,
            sortColumn
          )
        }

        else -> {
          throw IllegalArgumentException("Invalid sort order: $sortOrder")
        }
      }.map {
        val streamer = streamerRepo.getStreamerById(StreamerId(it.streamData.streamerId))
          ?: throw IllegalStateException("Streamer with ID ${it.streamData.streamerId} not found.")
        val stream = StreamData(it.streamData, streamer)
        UploadData(it.uploadData, stream)
      }
    }
  }

  override suspend fun countAllUploadData(
    status: List<Int>?,
    filter: String?,
    streamerId: Collection<StreamerId>?,
  ): Long =
    withIOContext {
      uploadDataDao.countAllByFilter(
        status ?: UploadState.intValues(),
        filter ?: "",
        streamerId?.map { it.value } ?: emptyList(),
        streamerId?.isEmpty() != false
      )
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
      uploadResultDao.getAllWithDataDesc().map {
        val streamData = streamsRepo.getStreamDataById(StreamDataId(it.uploadData.streamDataId))
          ?: throw IllegalStateException("Stream data with ID ${it.uploadData.streamDataId} not found.")
        val data = UploadData(it.uploadData, streamData)
        UploadResult(it.uploadResult, data)
      }
    }
  }

  /**
   * Saves an upload result.
   *
   * This function saves an upload result to the database.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param uploadResult The upload result to save
   */
  override suspend fun saveResult(uploadResult: UploadResult): UploadResult = withIOContext {
    val id = uploadResultDao.insert(uploadResult.toEntity())
    val today = github.hua0512.utils.getTodayStart().epochSeconds
    val todayStats = statsDao.getBetweenTimeOrderedDesc(today, today + 86400000).firstOrNull()

    if (todayStats == null) {
      if (uploadResult.isSuccess) {
        statsDao.insert(StatsEntity(0, today, 0, 1, 0))
      } else {
        statsDao.insert(StatsEntity(0, today, 0, 0, 1))
      }
    } else {
      if (uploadResult.isSuccess) {
        statsDao.update(todayStats.copy(uploads = todayStats.uploads + 1))
      } else {
        statsDao.update(todayStats.copy(failedUploads = todayStats.failedUploads + 1))
      }
    }
    // update the upload result ID
    uploadResult.copy(id = id)
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
      uploadDataDao.getByIdWithStreamAndAction(uploadDataId)?.let {
        val streamer = streamerRepo.getStreamerById(StreamerId(it.streamData.streamerId))
          ?: throw IllegalStateException("Streamer with ID ${it.streamData.streamerId} not found.")
        val stream = StreamData(it.streamData, streamer)
        UploadData(it.uploadData, stream)
      }
    }
  }

  override suspend fun insertUploadData(uploadData: UploadData): UploadData = withIOContext {
    val id = uploadDataDao.insert(uploadData.toEntity())
    uploadData.copy(id = id)
  }


  override suspend fun findUploadDataByPath(path: String): UploadData? {
    return withIOContext {
      uploadDataDao.findUploadDataByPath(path)?.let {
        val streamer = streamerRepo.getStreamerById(StreamerId(it.streamData.streamerId))
          ?: throw IllegalStateException("Streamer with ID ${it.streamData.streamerId} not found.")
        val stream = StreamData(it.streamData, streamer)
        UploadData(it.uploadData, stream)
      }
    }
  }

  /**
   * Updates upload data.
   * This function updates the upload data in the database.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   * @param uploadData The upload data to update
   * @return The updated upload data
   */
  override suspend fun updateUploadData(uploadData: UploadData): Boolean {
    return withIOContext {
      uploadDataDao.update(uploadData.toEntity()) == 1
    }
  }

  /**
   * Deletes an upload data.
   *
   * This function deletes an upload data from the database.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param uploadData The upload data to delete
   */
  override suspend fun deleteUploadData(uploadData: UploadData) {
    return withIOContext {
      if (uploadData.id == 0L) {
        throw IllegalArgumentException("Upload data ID is required for deletion.")
      }
      uploadDataDao.delete(uploadData.toEntity())
    }
  }

  override suspend fun deleteUploadData(ids: List<UploadDataId>) {
    return withIOContext {
      ids.forEach {
        val uploadData = uploadDataDao.getById(it) ?: return@forEach
        uploadDataDao.delete(uploadData)
      }
    }
  }


  /**
   * Deletes an upload result.
   *
   * This function deletes an upload result from the database.
   * It uses the IO dispatcher for the coroutine context to ensure that the database operation doesn't block the main thread.
   *
   * @param result The upload result to delete
   */
  override suspend fun deleteUploadResult(result: UploadResult) = withIOContext {
    uploadResultDao.delete(result.toEntity()) == 1
  }


  override suspend fun getUploadDataResults(uploadDataId: UploadDataId): List<UploadResult> {
    return withIOContext {
      val result = uploadDataDao.getUploadResults(uploadDataId)
      val first = result.keys.firstOrNull() ?: return@withIOContext emptyList()
      val uploadData = UploadData(first)
      // map upload results to UploadResult objects
      result[first]?.map { UploadResult(it, uploadData) } ?: emptyList()
    }
  }


  private suspend fun UploadDataWithStream.toUploadData(): UploadData {
    val streamer = streamerRepo.getStreamerById(StreamerId(streamData.streamerId))
      ?: throw IllegalStateException("Streamer with ID ${streamData.streamerId} not found.")
    val stream = StreamData(streamData, streamer)
    return UploadData(uploadData, stream)
  }
}
