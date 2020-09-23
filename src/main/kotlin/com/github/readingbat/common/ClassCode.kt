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

package com.github.readingbat.common

import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.common.CommonUtils.keyOf
import com.github.readingbat.common.FormFields.DISABLED_MODE
import com.github.readingbat.common.KeyConstants.CLASS_CODE_KEY
import com.github.readingbat.common.KeyConstants.CLASS_INFO_KEY
import com.github.readingbat.common.KeyConstants.DESC_FIELD
import com.github.readingbat.common.KeyConstants.TEACHER_FIELD
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.server.ReadingBatServer.usePostgres
import io.ktor.http.*
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction
import kotlin.time.measureTimedValue

internal data class ClassCode(val value: String) {
  val isNotEnabled by lazy { value == DISABLED_MODE || value.isBlank() }
  val isEnabled by lazy { !isNotEnabled }
  val classCodeEnrollmentKey by lazy { keyOf(CLASS_CODE_KEY, value) }
  val classInfoKey by lazy { keyOf(CLASS_INFO_KEY, value) }

  val displayedValue get() = if (value == DISABLED_MODE) "" else value

  val classId
    get() =
      measureTimedValue {
        transaction {
          Classes
            .slice(Classes.id)
            .select { Classes.classCode eq value }
            .map { it[Classes.id].value }
            .firstOrNull() ?: throw InvalidConfigurationException("Missing class code $value")
        }
      }.let {
        logger.info { "Looked up classId in ${it.duration}" }
        it.value
      }

  fun isNotValid(redis: Jedis) = !isValid(redis)

  fun isValid(redis: Jedis) = redis.exists(classCodeEnrollmentKey) ?: false

  fun fetchEnrollees(redis: Jedis?): List<User> =
    if (redis.isNull() || isNotEnabled)
      emptyList()
    else
      (redis.smembers(classCodeEnrollmentKey) ?: emptySet())
        .filter { it.isNotEmpty() }
        .map { it.toUser(redis, null) }

  fun addEnrolleePlaceholder(tx: Transaction) {
    tx.sadd(classCodeEnrollmentKey, "")
  }

  fun addEnrollee(user: User, tx: Transaction) {
    if (usePostgres)
      transaction {
        Enrollees
          .insert { row ->
            row[classesRef] = classId
            row[userRef] = user.dbmsId
          }
      }
    else
      tx.sadd(classCodeEnrollmentKey, user.userId)
  }

  fun removeEnrollee(user: User, tx: Transaction) {
    if (usePostgres)
      transaction {
        Enrollees.deleteWhere { (Enrollees.classesRef eq classId) and (Enrollees.userRef eq user.dbmsId) }
      }
    else
      tx.srem(classCodeEnrollmentKey, user.userId)
  }

  fun deleteAllEnrollees(tx: Transaction) {
    if (usePostgres)
      transaction {
        Enrollees.deleteWhere { Enrollees.classesRef eq classId }
      }
    else
      tx.del(classCodeEnrollmentKey)
  }

  fun initializeWith(classDesc: String, user: User, tx: Transaction) {
    if (usePostgres) {
      // Work done in user.addClassCode()
    }
    else
      tx.hset(classInfoKey, mapOf(DESC_FIELD to classDesc, TEACHER_FIELD to user.userId))
  }

  fun fetchClassDesc(redis: Jedis?, quoted: Boolean = false) =
    if (usePostgres)
      transaction {
        (Classes
          .slice(Classes.description)
          .select { Classes.classCode eq value }
          .map { it[Classes.description] }
          .firstOrNull() ?: "Missing description")
          .also { logger.info { "fetchClassDesc() returned $it for $value" } }
      }
    else {
      if (redis.isNull())
        "Description unavailable"
      else
        redis.hget(classInfoKey, DESC_FIELD) ?: "Missing description"
    }
      .let { if (quoted) it.toDoubleQuoted() else it }

  fun toDisplayString(redis: Jedis?) = "${fetchClassDesc(redis, true)} [$value]"

  fun fetchClassTeacherId(redis: Jedis) =
    if (usePostgres)
      transaction {
        ((Classes innerJoin Users)
          .slice(Users.userId)
          .select { (Classes.classCode eq value) and (Classes.userRef eq Users.id) }
          .map { it[Users.userId] }
          .firstOrNull() ?: "").also { logger.info { "fetchClassTeacherId() returned $it" } }
      }
    else
      redis.hget(classInfoKey, TEACHER_FIELD) ?: ""

  override fun toString() = value

  companion object : KLogging() {
    internal val DISABLED_CLASS_CODE = ClassCode(DISABLED_MODE)

    internal fun newClassCode() = ClassCode(randomId(15))

    internal fun Parameters.getClassCode(parameterName: String) =
      this[parameterName]?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
  }
}