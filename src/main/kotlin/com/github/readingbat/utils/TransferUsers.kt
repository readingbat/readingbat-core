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
import com.github.readingbat.common.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.common.KeyConstants.AUTH_KEY
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.KeyConstants.LIKE_DISLIKE_KEY
import com.github.readingbat.common.KeyConstants.USER_INFO_KEY
import com.github.readingbat.common.RedisUtils.scanKeys
import com.github.readingbat.common.User.Companion.EMAIL_FIELD
import com.github.readingbat.common.User.Companion.NAME_FIELD
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.fetchEnrolledClassCode
import com.github.readingbat.common.User.Companion.fetchPreviousTeacherClassCode
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.server.keyOf
import com.github.readingbat.utils.TransferUsers.UserChallengeInfo.md5
import com.github.readingbat.utils.TransferUsers.UserChallengeInfo.userRef
import mu.KLogging
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

internal object TransferUsers : KLogging() {

  object KotlinLoggingSqlLogger : SqlLogger {
    override
    fun log(context: StatementContext, transaction: Transaction) {
      logger.info { "SQL: ${context.expandArgs(transaction)}" }
    }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    Database.connect(hikari())

    transaction {
      addLogger(KotlinLoggingSqlLogger)

      transform(RedisAdmin.local)
    }
  }

  object Users : LongIdTable() {
    val created = datetime("created")
    val userId = varchar("user_id", 25)
    val email = text("email")
    val name = text("name")
    val salt = text("salt")
    val digest = text("digest")
    val enrolledClassCode = text("enrolled_class_code")

    override fun toString(): String = userId.toString()
  }

  object UserBrowserSessions : LongIdTable("user_browser_sessions") {
    val created = datetime("created")
    val userRef = long("user_ref")
    val session_id = text("session_id")
    val activeClassCode = text("active_class_code")
    val previousTeacherClassCode = text("previous_teacher_class_code")

    override fun toString(): String = "$id $activeClassCode $previousTeacherClassCode"
  }

  object UserChallengeInfo : LongIdTable("user_challenge_info") {
    val created = datetime("created")
    val userRef = long("user_ref")
    val md5 = text("md5")
    val correct = bool("correct")
    val likedislike = short("likedislike")

    override fun toString(): String = "$id $md5 $correct $likedislike"
  }

  internal fun transform(url: String) {
    RedisUtils.withNonNullRedis(url) { redis ->
      println(
        redis.scanKeys(keyOf(USER_INFO_KEY, "*"))
          .filter { (redis.hget(it, NAME_FIELD) ?: "").isNotBlank() }
          .onEach { ukey ->
            val userId = ukey.split(KEY_SEP)[1]
            val user1 = userId.toUser(null)
            val id =
              Users.insertAndGetId { record ->
                record[this.userId] = userId
                record[email] = user1.email(redis).value
                record[name] = user1.name(redis)
                record[salt] = user1.salt(redis)
                record[digest] = user1.digest(redis)
                record[enrolledClassCode] = user1.fetchEnrolledClassCode(redis).value
              }
            println("Created user id: $id")

            redis.scanKeys(user1.userInfoBrowserQueryKey)
              .forEach { bkey ->
                val browser_sessions_id = bkey.split(KEY_SEP)[2]
                val user2 = userId.toUser(BrowserSession(browser_sessions_id))
                val activeClassCode2 = user2.fetchActiveClassCode(redis)
                val previousClassCode2 = user2.fetchPreviousTeacherClassCode(redis)
                //println("$bkey $browser_sessions_id ${redis.hgetAll(user2.browserSpecificUserInfoKey)} $activeClassCode2 $previousClassCode2")

                UserBrowserSessions.insertAndGetId { record ->
                  record[userRef] = id.value
                  record[session_id] = browser_sessions_id
                  record[activeClassCode] = activeClassCode2.value
                  record[previousTeacherClassCode] = previousClassCode2.value
                }
              }

            val userChallengeIndex = Index(listOf(userRef, md5), true, "user_ref_md5_unique")

            redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, AUTH_KEY, userId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                val user_id = key.split(KEY_SEP)[2]
                require(userId == user_id)
                //println("$key $user_id ${redis[key]}")

                UserChallengeInfo.upsert(null, userChallengeIndex) { record ->
                  record[userRef] = id.value
                  record[md5] = key.split(KEY_SEP)[3]
                  record[correct] = redis[key].toBoolean()
                }
              }

            redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, AUTH_KEY, userId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                val user_id = key.split(KEY_SEP)[2]
                require(userId == user_id)
                //println("$key $user_id ${redis[key]}")

                UserChallengeInfo.upsert(null, userChallengeIndex) { record ->
                  record[userRef] = id.value
                  record[md5] = key.split(KEY_SEP)[3]
                  record[likedislike] = redis[key].toShort()
                }
              }

            redis.scanKeys(keyOf(ANSWER_HISTORY_KEY, AUTH_KEY, userId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                val user_id = key.split(KEY_SEP)[2]
                require(userId == user_id)
                println("$key ${redis.get(key)}")
              }

            redis.scanKeys(keyOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, userId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                val user_id = key.split(KEY_SEP)[2]
                require(userId == user_id)
                println("$key ${redis.hgetAll(key)}")
              }
          }
          .joinToString("\n") {
            "$it  ${redis.hget(it, NAME_FIELD)} ${redis.hget(it, EMAIL_FIELD)}"
          })
    }
  }

}

inline fun <T : Table> T.upsert(conflictColumn: Column<*>? = null,
                                conflictIndex: Index? = null,
                                body: T.(UpsertStatement<Number>) -> Unit) =
  UpsertStatement<Number>(this, conflictColumn, conflictIndex)
    .apply {
      body(this)
      execute(TransactionManager.current())
    }

class UpsertStatement<Key : Any>(table: Table,
                                 conflictColumn: Column<*>? = null,
                                 conflictIndex: Index? = null) : InsertStatement<Key>(table, false) {
  val indexName: String
  val indexColumns: List<Column<*>>

  init {
    when {
      conflictIndex != null -> {
        indexName = conflictIndex.indexName
        indexColumns = conflictIndex.columns
      }
      conflictColumn != null -> {
        indexName = conflictColumn.name
        indexColumns = listOf(conflictColumn)
      }
      else -> throw IllegalArgumentException()
    }
  }

  override fun prepareSQL(transaction: Transaction) =
    buildString {
      append(super.prepareSQL(transaction))
      append(" ON CONFLICT ON CONSTRAINT $indexName DO UPDATE SET ")
      values.keys.filter { it !in indexColumns }
        .joinTo(this) { "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}" }
    }
}


/*
fun <T : Table> T.insertOrUpdate(constrainName: String,
                                 vararg onDuplicateUpdateKeys: Column<*>,
                                 body: T.(InsertStatement<Number>) -> Unit) =
  InsertOrUpdate<Number>(constrainName, onDuplicateUpdateKeys, this)
    .apply {
      body(this)
      execute(TransactionManager.current())
    }

class InsertOrUpdate<Key : Any>(private val constrainName: String,
                                private val onDuplicateUpdateKeys: Array<out Column<*>>,
                                table: Table,
                                isIgnore: Boolean = false) : InsertStatement<Key>(table, isIgnore) {
  override fun prepareSQL(transaction: Transaction) =
    super.prepareSQL(transaction) +
        if (onDuplicateUpdateKeys.isNotEmpty()) {
          val updateStr =
            onDuplicateUpdateKeys.joinToString {
              "${transaction.identity(it)}=VALUES(${transaction.identity(it)})"
            }
          " ON CONFLICT ON CONSTRAINT $constrainName DO UPDATE SET $updateStr"
        }
        else ""
}

 */

