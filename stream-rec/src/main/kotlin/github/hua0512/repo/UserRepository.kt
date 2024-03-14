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

import github.hua0512.dao.UserDao
import github.hua0512.data.User
import github.hua0512.data.UserId
import github.hua0512.utils.withIOContext

/**
 * Repository for User
 * @author hua0512
 * @date : 2024/3/14 18:57
 */

class UserRepository(val dao: UserDao) : UserRepo {
  override suspend fun getUserById(id: UserId): User? {
    return withIOContext {
      dao.getUserById(id)?.let { User(it) }
    }
  }

  override suspend fun getUserByName(name: String): User? {
    return withIOContext {
      dao.getUserByUsername(name)?.let { User(it) }
    }
  }

  override suspend fun createUser(newUser: User): User {
    return withIOContext {
      val user = dao.createUser(newUser.toEntity())
      newUser.copy(id = user.id.toInt())
    }
  }

  override suspend fun deleteUser(id: UserId): Boolean {
    return withIOContext {
      dao.deleteUser(id)
    }
  }

  override suspend fun updateUser(user: User) {
    return withIOContext {
      dao.updateUser(user.toEntity())
    }
  }
}