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
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.posts.ChallengeHistory
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

  object Users : LongIdTable() {
    val created = datetime("created")
    val updated = datetime("updated")
    val userId = varchar("user_id", 25)
    val email = text("email")
    val name = text("name")
    val salt = text("salt")
    val digest = text("digest")
    val enrolledClassCode = text("enrolled_class_code")

    override fun toString(): String = userId.toString()
  }

  object BrowserSessions : LongIdTable("browser_sessions") {
    val created = datetime("created")
    val updated = datetime("updated")
    val session_id = text("session_id")
    val userRef = long("user_ref")
    val activeClassCode = text("active_class_code")
    val previousTeacherClassCode = text("previous_teacher_class_code")

    override fun toString(): String = "$id $activeClassCode $previousTeacherClassCode"
  }

  object UserChallengeInfo : LongIdTable("user_challenge_info") {
    val created = datetime("created")
    val updated = datetime("updated")
    val userRef = long("user_ref")
    val md5 = text("md5")
    val correct = bool("correct")
    val likedislike = short("likedislike")
    val answersJson = text("answers_json")

    override fun toString(): String = "$id $md5 $correct $likedislike"
  }

  object UserAnswerHistory : LongIdTable("user_answer_history") {
    val created = datetime("created")
    val updated = datetime("updated")
    val userRef = long("user_ref")
    val md5 = text("md5")
    val invocation = text("invocation")
    val correct = bool("correct")
    val incorrectAttempts = integer("incorrect_attempts")
    val historyJson = text("history_json")

    override fun toString(): String = "$id $md5 $invocation $correct"
  }

  internal fun transform(url: String) {
    RedisUtils.withNonNullRedis(url) { redis ->
      println(
        redis.scanKeys(keyOf(USER_INFO_KEY, "*"))
          .filter { (redis.hget(it, NAME_FIELD) ?: "").isNotBlank() }
          .onEach { ukey ->
            val keyUserId = ukey.split(KEY_SEP)[1]
            val user1 = keyUserId.toUser(null)
            val id =
              Users.insertAndGetId { record ->
                record[userId] = keyUserId
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
                val user2 = keyUserId.toUser(BrowserSession(browser_sessions_id))
                val activeClassCode2 = user2.fetchActiveClassCode(redis)
                val previousClassCode2 = user2.fetchPreviousTeacherClassCode(redis)
                //println("$bkey $browser_sessions_id ${redis.hgetAll(user2.browserSpecificUserInfoKey)} $activeClassCode2 $previousClassCode2")

                BrowserSessions.insertAndGetId { record ->
                  record[userRef] = id.value
                  record[session_id] = browser_sessions_id
                  record[activeClassCode] = activeClassCode2.value
                  record[previousTeacherClassCode] = previousClassCode2.value
                }
              }

            val userChallengeIndex = Index(listOf(userRef, md5), true, "user_challenge_info_unique")

            redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, AUTH_KEY, keyUserId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(keyUserId == key.split(KEY_SEP)[2])
                //println("$key userId ${redis[key]}")

                UserChallengeInfo.upsert(conflictIndex = userChallengeIndex) { record ->
                  record[userRef] = id.value
                  record[md5] = key.split(KEY_SEP)[3]
                  record[updated] = DateTime.now(UTC)
                  record[correct] = redis[key].toBoolean()
                }
              }

            redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, AUTH_KEY, keyUserId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(keyUserId == key.split(KEY_SEP)[2])
                //println("$key userId ${redis[key]}")

                UserChallengeInfo.upsert(conflictIndex = userChallengeIndex) { record ->
                  record[userRef] = id.value
                  record[md5] = key.split(KEY_SEP)[3]
                  record[updated] = DateTime.now(UTC)
                  record[likedislike] = redis[key].toShort()
                }
              }

            redis.scanKeys(keyOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, keyUserId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(keyUserId == key.split(KEY_SEP)[2])
                //println("$key ${redis.hgetAll(key)}")

                UserChallengeInfo.upsert(conflictIndex = userChallengeIndex) { record ->
                  record[userRef] = id.value
                  record[md5] = key.split(KEY_SEP)[3]
                  record[updated] = DateTime.now(UTC)
                  record[answersJson] = gson.toJson(redis.hgetAll(key))
                }
              }

            // md5 has names and invocation in it
            redis.scanKeys(keyOf(ANSWER_HISTORY_KEY, AUTH_KEY, keyUserId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(keyUserId == key.split(KEY_SEP)[2])
                //println("$key ${redis.get(key)}")

                UserAnswerHistory.insertAndGetId() { record ->
                  val history = gson.fromJson(redis[key], ChallengeHistory::class.java)

                  record[userRef] = id.value
                  record[md5] = key.split(KEY_SEP)[3]
                  record[invocation] = history.invocation.value
                  record[correct] = history.correct
                  record[incorrectAttempts] = history.incorrectAttempts
                  record[historyJson] = gson.toJson(history.answers)
                }
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

