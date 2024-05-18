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

package github.hua0512.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Upsert

/**
 * Base DAO interface with CRUD operations
 * @author hua0512
 * @date : 2024/2/19 10:59
 */
interface BaseDao<T> {

  /**
   * Insert data
   * @param entity data to insert
   * @return row id
   */
  @Insert
  suspend fun insert(entity: T): Long

  /**
   * Insert a list of data
   * @param entity list of data to insert
   * @return row id
   */
  @Insert
  suspend fun insert(entity: List<T>): List<Long>

  /**
   * Update data
   * @param entity data to update
   * @return number of rows updated
   */
  @Update
  suspend fun update(entity: T): Int

  /**
   * Update a list of data
   * @param entity list of data to update
   * @return number of rows updated
   */
  @Update
  suspend fun update(entity: List<T>): Int

  /**
   * Upsert data
   * @param entity data to upsert
   */
  @Upsert
  suspend fun upsert(entity: T)

  /**
   * Delete data
   * @param entity data to delete
   * @return number of rows deleted
   */
  @Delete
  suspend fun delete(entity: T): Int

  /**
   * Delete a list of data
   * @param entity data to delete
   * @return number of rows deleted
   */
  @Delete
  suspend fun delete(entity: List<T>): Int
}