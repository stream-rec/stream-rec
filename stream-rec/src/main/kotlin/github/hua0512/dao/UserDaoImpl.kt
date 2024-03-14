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

import github.hua0512.StreamRecDatabase
import github.hua0512.data.UserId
import github.hua0512.utils.UserEntity

/**
 * @author hua0512
 * @date : 2024/3/14 18:49
 */
class UserDaoImpl(override val database: StreamRecDatabase) : UserDao, BaseDaoImpl {
  override fun getUserById(id: UserId): UserEntity? {
    return queries.getUserById(id.value).executeAsOneOrNull()
  }

  override fun getUserByUsername(username: String): UserEntity? {
    return queries.getUserByUsername(username).executeAsOneOrNull()
  }

  override fun createUser(user: UserEntity): UserEntity {
    return user.run {
      queries.insertUser(user.username, user.password, user.role)
      val id = queries.getLastUserId().executeAsOne()
      user.copy(id = id)
    }
  }

  override fun updateUser(user: UserEntity): UserEntity {
    return user.run {
      queries.updateUser(user.username, user.password, user.role, user.id)
      user
    }
  }

  override fun deleteUser(id: UserId): Boolean {
    return queries.run {
      deleteUser(id.value)
      getUserById(id.value).executeAsOneOrNull() == null
    }
  }
}