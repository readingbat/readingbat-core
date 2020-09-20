/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.readingbat.utils

import com.github.pambrose.common.redis.RedisUtils
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.KeyConstants.USER_INFO_KEY
import com.github.readingbat.common.RedisUtils.scanKeys
import com.github.readingbat.common.User.Companion.EMAIL_FIELD
import com.github.readingbat.common.User.Companion.NAME_FIELD
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.fetchEnrolledClassCode
import com.github.readingbat.common.User.Companion.fetchPreviousTeacherClassCode
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.server.keyOf
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction

internal object TransferUsers {

  @JvmStatic
  fun main(args: Array<String>) {
    Database.connect(hikari())

    transaction {
      addLogger(StdOutSqlLogger)

      showAll(RedisAdmin.local)
    }
  }

  object Users : LongIdTable() {
    val created = datetime("created")
    val user_id = varchar("user_id", 25)
    val email = text("email")
    val name = text("name")
    val salt = text("salt")
    val digest = text("digest")
    val enrolled_class_code = text("enrolled_class_code")

    override fun toString(): String = user_id.toString()
  }


  internal fun showAll(url: String) {
    RedisUtils.withNonNullRedis(url) { redis ->
      println(
        redis.scanKeys(keyOf(USER_INFO_KEY, "*"))
          .filter { (redis.hget(it, NAME_FIELD) ?: "").isNotBlank() }
          .onEach { ukey ->
            val userId = ukey.split(KEY_SEP)[1]
            val user1 = userId.toUser(null)
            val id =
              Users.insertAndGetId { record ->
                record[user_id] = userId
                record[email] = user1.email(redis).value
                record[name] = user1.name(redis)
                record[salt] = user1.salt(redis)
                record[digest] = user1.digest(redis)
                record[enrolled_class_code] = user1.fetchEnrolledClassCode(redis).value
              }
            println("Created user id: $id")

            redis.scanKeys(user1.userInfoBrowserQueryKey)
              .forEach { bkey ->
                val browser_sessions_id = bkey.split(KEY_SEP)[2]
                val user2 = userId.toUser(BrowserSession(browser_sessions_id))
                val activeClassCode = user2.fetchActiveClassCode(redis)
                val previousClassCode = user2.fetchPreviousTeacherClassCode(redis)
                println("$bkey $browser_sessions_id ${redis.hgetAll(user2.browserSpecificUserInfoKey)} $activeClassCode $previousClassCode")
              }


          }
          .joinToString("\n") {
            "$it  ${redis.hget(it, NAME_FIELD)} ${redis.hget(it, EMAIL_FIELD)}"
          })
    }
  }

}