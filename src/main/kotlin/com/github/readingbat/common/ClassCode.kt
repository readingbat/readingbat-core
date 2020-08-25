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
import com.github.readingbat.common.FormFields.DISABLED_MODE
import com.github.readingbat.common.KeyConstants.CLASS_CODE_KEY
import com.github.readingbat.common.KeyConstants.CLASS_INFO_KEY
import com.github.readingbat.common.KeyConstants.DESC_FIELD
import com.github.readingbat.common.KeyConstants.TEACHER_FIELD
import com.github.readingbat.server.User
import com.github.readingbat.server.User.Companion.toUser
import com.github.readingbat.server.keyOf
import io.ktor.http.*
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction

internal data class ClassCode(val value: String) {
  val isNotEnabled by lazy { value == DISABLED_MODE || value.isBlank() }
  val isEnabled by lazy { !isNotEnabled }
  val classCodeEnrollmentKey by lazy { keyOf(CLASS_CODE_KEY, value) }
  val classInfoKey by lazy { keyOf(CLASS_INFO_KEY, value) }
  val displayedValue get() = if (value == DISABLED_MODE) "" else value

  fun isValid(redis: Jedis) = redis.exists(classCodeEnrollmentKey) ?: false

  fun isNotValid(redis: Jedis) = !isValid(redis)

  fun fetchEnrollees(redis: Jedis?): List<User> =
    if (redis.isNull() || isNotEnabled)
      emptyList()
    else
      (redis.smembers(classCodeEnrollmentKey) ?: emptySet())
        .filter { it.isNotEmpty() }
        .map { it.toUser(null) }

  fun addEnrolleePlaceholder(tx: Transaction) {
    tx.sadd(classCodeEnrollmentKey, "")
  }

  fun addEnrollee(user: User, tx: Transaction) {
    tx.sadd(classCodeEnrollmentKey, user.id)
  }

  fun removeEnrollee(user: User, tx: Transaction) {
    tx.srem(classCodeEnrollmentKey, user.id)
  }

  fun deleteAllEnrollees(tx: Transaction) {
    tx.del(classCodeEnrollmentKey)
  }

  fun initializeWith(classDesc: String, user: User, tx: Transaction) {
    tx.hset(classInfoKey, mapOf(DESC_FIELD to classDesc, TEACHER_FIELD to user.id))
  }

  fun fetchClassDesc(redis: Jedis, quoted: Boolean = false) =
    (redis.hget(classInfoKey, DESC_FIELD) ?: "Missing description").let { if (quoted) it.toDoubleQuoted() else it }

  fun fetchClassTeacherId(redis: Jedis) = redis.hget(classInfoKey, TEACHER_FIELD) ?: ""

  override fun toString() = value

  companion object {
    internal val DISABLED_CLASS_CODE = ClassCode(DISABLED_MODE)

    internal fun newClassCode() = ClassCode(randomId(15))

    internal fun Parameters.getClassCode(parameterName: String) =
      this[parameterName]?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
  }
}