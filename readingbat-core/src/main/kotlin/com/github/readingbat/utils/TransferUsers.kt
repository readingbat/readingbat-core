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

/*
internal object TransferUsers : KLogging() {

  internal const val EMAIL_FIELD = "email"
  internal const val NAME_FIELD = "name"

  internal fun hikari() =
    HikariDataSource(
      HikariConfig()
        .apply {
          driverClassName = "com.impossibl.postgres.jdbc.PGDriver"
          jdbcUrl =
            "jdbc:pgsql://readingbat-postgres-do-user-329986-0.b.db.ondigitalocean.com:25060/readingbat?ssl.mode=Require"
          username = "readingbat"
          maximumPoolSize = 10
          isAutoCommit = false
          transactionIsolation = "TRANSACTION_REPEATABLE_READ"
          validate()
        })

  @JvmStatic
  fun main(args: Array<String>) {

    //usePostgres = false

    Database.connect(hikari())

    transform(RedisAdmin.local)
  }

  private fun transform(url: String) {
    withNonNullRedis(url) { redis ->

      val sessionMap = mutableMapOf<String, Long>()

      transaction {
        addLogger(KotlinLoggingSqlLogger)

        listOf(
          redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, "*", "*")).toList(),
          redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, NO_AUTH_KEY, "*", "*")).toList(),
          redis.scanKeys(keyOf(CHALLENGE_ANSWERS_KEY, NO_AUTH_KEY, "*", "*")).toList(),
          redis.scanKeys(keyOf(ANSWER_HISTORY_KEY, NO_AUTH_KEY, "*", "*")).toList(),
          redis.scanKeys(keyOf(USER_INFO_BROWSER_KEY, "*", "*")).toList())
          .flatten()
          .map { it.split(KEY_SEP)[2] }  // pull out the browser session_id value
          .filter { it != "unassigned" }
          .sorted()
          .distinct()
          .forEach { sessionId ->

            val sessionDbmsId =
              BrowserSessions
                .insertAndGetId { row ->
                  logger.info { "Inserting browser session: $sessionId" }
                  row[session_id] = sessionId
                }.value

            sessionMap[sessionId] = sessionDbmsId

            redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, sessionId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(sessionId == key.split(KEY_SEP)[2])
                //println("$key ${redis[key]}")

                SessionChallengeInfo
                  .upsert(conflictIndex = sessionChallengeInfoIndex) { row ->
                    row[sessionRef] = sessionDbmsId
                    row[md5] = key.split(KEY_SEP)[3]
                    row[updated] = DateTime.now(UTC)
                    row[allCorrect] = redis[key].toBoolean()
                  }
              }

            redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, NO_AUTH_KEY, sessionId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(sessionId == key.split(KEY_SEP)[2])

                SessionChallengeInfo
                  .upsert(conflictIndex = sessionChallengeInfoIndex) { row ->
                    row[sessionRef] = sessionDbmsId
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

                SessionChallengeInfo
                  .upsert(conflictIndex = sessionChallengeInfoIndex) { row ->
                    row[sessionRef] = sessionDbmsId
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

                SessionAnswerHistory
                  .insertAndGetId { row ->
                    val history = gson.fromJson(redis[key], ChallengeHistory::class.java)
                    row[sessionRef] = sessionDbmsId
                    row[md5] = key.split(KEY_SEP)[3]
                    row[invocation] = history.invocation.value
                    row[correct] = history.correct
                    row[incorrectAttempts] = history.incorrectAttempts
                    row[historyJson] = gson.toJson(history.answers)
                  }
              }
          }
      }

      transaction {
        addLogger(KotlinLoggingSqlLogger)
        val userMap = mutableMapOf<String, Long>()

        // Preload all users and stick ids in map.
        redis.scanKeys(keyOf(USER_INFO_KEY, "*"))
          .filter { (redis.hget(it, NAME_FIELD) ?: "").isNotBlank() }
          .forEach { ukey ->
            val userId = ukey.split(KEY_SEP)[1]
            val user = toUser(userId)
            val id =
              Users
                .insertAndGetId { row ->
                  row[Users.userId] = userId
                  row[email] = user.email.value
                  row[name] = user.fullName.value
                  row[salt] = user.salt
                  row[digest] = user.digest
                  row[enrolledClassCode] = user.enrolledClassCode.value
                  row[defaultLanguage] = defaultLanguageType.languageName.value
                }.value
            userMap[userId] = id
            logger.info { "Created user id: $id for $userId" }
          }


        redis.scanKeys(keyOf(USER_INFO_KEY, "*"))
          .filter { (redis.hget(it, NAME_FIELD) ?: "").isNotBlank() }
          .onEach { ukey ->
            val userId = ukey.split(KEY_SEP)[1]
            val user = toUser(userId)
            logger.info { "Fetched user ${userMap[userId]} ${user.email} for $userId" }

            redis.scanKeys(""/*user.userClassesKey*/)
              .forEach { key ->
                require(userId == key.split(KEY_SEP)[1])
                //println("ClassCodes: $key ${redis.smembers(user.userClassesKey)}")

                redis.smembers(""/*user.userClassesKey*/)
                  .map { ClassCode(it) }
                  .forEach { classCode ->
                    logger.info { "Inserting Classes ${userMap[userId]} ${user.email} ${classCode.value}" }
                    val classCodeId =
                      Classes
                        .insertAndGetId { row ->
                          row[userRef] =
                            userMap[userId] ?: error("Invalid user id $userId")
                          row[Classes.classCode] = classCode.value
                          row[description] = classCode.fetchClassDesc()
                        }.value

                    redis.smembers(classCode.classCodeEnrollmentKey)
                      .filter { it.isNotBlank() }
                      .forEach { enrolleeId ->
                        Enrollees
                          .insert { row ->
                            row[classesRef] = classCodeId
                            row[userRef] =
                              userMap[enrolleeId] ?: error("Invalid user id $enrolleeId")
                          }
                      }
                  }
              }

            redis.scanKeys(""/*user.userInfoBrowserQueryKey*/)
              .map { it.split(KEY_SEP)[2] }
              .filter { it != "unassigned" }
              .forEach { sessionId ->
                val browserUser = toUser(userId, BrowserSession(sessionId))
                val activeClassCode = fetchActiveClassCode(browserUser)
                val previousClassCode = fetchPreviousTeacherClassCode(browserUser)
                //println("$key $browser_sessions_id ${redis.hgetAll(user2.browserSpecificUserInfoKey)} $activeClassCode $previousClassCode")

                UserSessions
                  .upsert(conflictIndex = userSessionIndex) { row ->
                    row[sessionRef] =
                      sessionMap[sessionId]
                        ?: error("Invalid session id $sessionId $sessionMap")
                    row[userRef] = userMap[userId] ?: error("Invalid user id $userId")
                    row[UserSessions.activeClassCode] = activeClassCode.value
                    row[previousTeacherClassCode] = previousClassCode.value
                  }
              }

            redis.scanKeys(keyOf(CORRECT_ANSWERS_KEY, AUTH_KEY, userId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(userId == key.split(KEY_SEP)[2])
                //println("$key userId ${redis[key]}")

                UserChallengeInfo
                  .upsert(conflictIndex = userChallengeInfoIndex) { row ->
                    row[userRef] = userMap[userId] ?: error("Invalid user id $userId")
                    row[md5] = key.split(KEY_SEP)[3]
                    row[updated] = DateTime.now(UTC)
                    row[allCorrect] = redis[key].toBoolean()
                  }
              }

            redis.scanKeys(keyOf(LIKE_DISLIKE_KEY, AUTH_KEY, userId, "*"))
              .filter { it.split(KEY_SEP).size == 4 }
              .forEach { key ->
                require(userId == key.split(KEY_SEP)[2])
                //println("$key userId ${redis[key]}")

                UserChallengeInfo
                  .upsert(conflictIndex = userChallengeInfoIndex) { row ->
                    row[userRef] = userMap[userId] ?: error("Invalid user id $userId")
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

                UserChallengeInfo
                  .upsert(conflictIndex = userChallengeInfoIndex) { row ->
                    row[userRef] = userMap[userId] ?: error("Invalid user id $userId")
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

                UserAnswerHistory
                  .insertAndGetId { row ->
                    val history = gson.fromJson(redis[key], ChallengeHistory::class.java)
                    row[userRef] = userMap[userId] ?: error("Invalid user id $userId")
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
}
*/

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

