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
import com.github.readingbat.common.*
import com.github.readingbat.common.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.common.KeyConstants.AUTH_KEY
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.KeyConstants.LIKE_DISLIKE_KEY
import com.github.readingbat.common.KeyConstants.NO_AUTH_KEY
import com.github.readingbat.common.KeyConstants.USER_INFO_KEY
import com.github.readingbat.common.RedisUtils.scanKeys
import com.github.readingbat.common.User.Companion.EMAIL_FIELD
import com.github.readingbat.common.User.Companion.NAME_FIELD
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.fetchEnrolledClassCode
import com.github.readingbat.common.User.Companion.fetchPreviousTeacherClassCode
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.keyOf
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC

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

  internal fun transform(url: String) {
    RedisUtils.withNonNullRedis(url) { redis ->
      val userChallengeIndex =
        Index(listOf(UserChallengeInfo.userRef, UserChallengeInfo.md5), true, "user_challenge_info_unique")
      val sessionChallengeIndex =
        Index(listOf(SessionChallengeInfo.sessionRef, SessionChallengeInfo.md5), true, "session_challenge_info_unique")
      val browserSessionIndex = Index(listOf(BrowserSessions.session_id), true, "browser_sessions_unique")

      listOf(redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, "*", "*")).toList(),
             redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, NO_AUTH_KEY, "*", "*")).toList(),
             redis.scanKeys(keyOf(CHALLENGE_ANSWERS_KEY, NO_AUTH_KEY, "*", "*")).toList(),
             redis.scanKeys(keyOf(ANSWER_HISTORY_KEY, NO_AUTH_KEY, "*", "*")).toList())
        .flatten()
        .map { it.split(KEY_SEP)[2] }  // pull out the browser session_id value
        .sorted()
        .distinct()
        .onEach { sessionId ->

          val dbmsSessionId =
            BrowserSessions.insertAndGetId { row ->
              row[session_id] = sessionId
            }

          redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, sessionId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(sessionId == key.split(KEY_SEP)[2])
              println("$key ${redis[key]}")

              SessionChallengeInfo.upsert(conflictIndex = sessionChallengeIndex) { row ->
                row[sessionRef] = dbmsSessionId.value
                row[md5] = key.split(KEY_SEP)[3]
                row[updated] = DateTime.now(UTC)
                row[correct] = redis[key].toBoolean()
              }
            }

          redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, NO_AUTH_KEY, sessionId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(sessionId == key.split(KEY_SEP)[2])
              //println("$key userId ${redis[key]}")

              SessionChallengeInfo.upsert(conflictIndex = sessionChallengeIndex) { row ->
                row[sessionRef] = dbmsSessionId.value
                row[md5] = key.split(KEY_SEP)[3]
                row[updated] = DateTime.now(UTC)
                row[likeDislike] = redis[key].toShort()
              }
            }

          redis.scanKeys(keyOf(CHALLENGE_ANSWERS_KEY, NO_AUTH_KEY, sessionId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(sessionId == key.split(KEY_SEP)[2])
              //println("$key ${redis.hgetAll(key)}")

              SessionChallengeInfo.upsert(conflictIndex = sessionChallengeIndex) { row ->
                row[sessionRef] = dbmsSessionId.value
                row[md5] = key.split(KEY_SEP)[3]
                row[updated] = DateTime.now(UTC)
                row[answersJson] = gson.toJson(redis.hgetAll(key))
              }
            }

          // md5 has names and invocation in it
          redis.scanKeys(keyOf(ANSWER_HISTORY_KEY, NO_AUTH_KEY, sessionId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(sessionId == key.split(KEY_SEP)[2])
              //println("$key ${redis.get(key)}")

              SessionAnswerHistory.insertAndGetId() { row ->
                val history = gson.fromJson(redis[key], ChallengeHistory::class.java)
                row[sessionRef] = dbmsSessionId.value
                row[md5] = key.split(KEY_SEP)[3]
                row[invocation] = history.invocation.value
                row[correct] = history.correct
                row[incorrectAttempts] = history.incorrectAttempts
                row[historyJson] = gson.toJson(history.answers)
              }
            }
        }


      redis.scanKeys(keyOf(USER_INFO_KEY, "*"))
        .filter { (redis.hget(it, NAME_FIELD) ?: "").isNotBlank() }
        .onEach { ukey ->
          val userId = ukey.split(KEY_SEP)[1]
          val user1 = userId.toUser(redis, null)
          val id =
            Users.insertAndGetId { row ->
              row[Users.userId] = userId
              row[email] = user1.email.value
              row[name] = user1.name.value
              row[salt] = user1.salt
              row[digest] = user1.digest
              row[enrolledClassCode] = user1.fetchEnrolledClassCode(redis).value
            }
          println("Created user id: $id")

          redis.scanKeys(user1.userInfoBrowserQueryKey)
            .forEach { bkey ->
              val sessions_id = bkey.split(KEY_SEP)[2]
              val user2 = userId.toUser(redis, BrowserSession(sessions_id))
              val activeClassCode = user2.fetchActiveClassCode(redis)
              val previousClassCode = user2.fetchPreviousTeacherClassCode(redis)
              //println("$bkey $browser_sessions_id ${redis.hgetAll(user2.browserSpecificUserInfoKey)} $activeClassCode $previousClassCode")

              BrowserSessions.upsert(conflictIndex = browserSessionIndex) { row ->
                row[userRef] = id.value
                row[session_id] = sessions_id
                row[BrowserSessions.activeClassCode] = activeClassCode.value
                row[previousTeacherClassCode] = previousClassCode.value
              }
            }

          redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, AUTH_KEY, userId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(userId == key.split(KEY_SEP)[2])
              //println("$key userId ${redis[key]}")

              UserChallengeInfo.upsert(conflictIndex = userChallengeIndex) { row ->
                row[userRef] = id.value
                row[md5] = key.split(KEY_SEP)[3]
                row[updated] = DateTime.now(UTC)
                row[correct] = redis[key].toBoolean()
              }
            }

          redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, AUTH_KEY, userId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(userId == key.split(KEY_SEP)[2])
              //println("$key userId ${redis[key]}")

              UserChallengeInfo.upsert(conflictIndex = userChallengeIndex) { row ->
                row[userRef] = id.value
                row[md5] = key.split(KEY_SEP)[3]
                row[updated] = DateTime.now(UTC)
                row[likeDislike] = redis[key].toShort()
              }
            }

          redis.scanKeys(keyOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, userId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(userId == key.split(KEY_SEP)[2])
              //println("$key ${redis.hgetAll(key)}")

              UserChallengeInfo.upsert(conflictIndex = userChallengeIndex) { row ->
                row[userRef] = id.value
                row[md5] = key.split(KEY_SEP)[3]
                row[updated] = DateTime.now(UTC)
                row[answersJson] = gson.toJson(redis.hgetAll(key))
              }
            }

          // md5 has names and invocation in it
          redis.scanKeys(keyOf(ANSWER_HISTORY_KEY, AUTH_KEY, userId, "*"))
            .filter { it.split(KEY_SEP).size == 4 }
            .forEach { key ->
              require(userId == key.split(KEY_SEP)[2])
              //println("$key ${redis.get(key)}")

              UserAnswerHistory.insertAndGetId() { row ->
                val history = gson.fromJson(redis[key], ChallengeHistory::class.java)

                row[userRef] = id.value
                row[md5] = key.split(KEY_SEP)[3]
                row[invocation] = history.invocation.value
                row[correct] = history.correct
                row[incorrectAttempts] = history.incorrectAttempts
                row[historyJson] = gson.toJson(history.answers)
              }
            }
        }
        .forEach {
          println("$it  ${redis.hget(it, NAME_FIELD)} ${redis.hget(it, EMAIL_FIELD)}")
        }
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

