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

package com.github.readingbat.misc

import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import com.github.readingbat.misc.FormFields.CLASSES_DISABLED
import com.github.readingbat.misc.KeyConstants.CLASS_CODE_KEY
import com.github.readingbat.misc.KeyConstants.CLASS_INFO_KEY
import com.github.readingbat.misc.KeyConstants.DESC_FIELD
import com.github.readingbat.misc.KeyConstants.KEY_SEP
import com.github.readingbat.misc.KeyConstants.TEACHER_FIELD
import com.github.readingbat.misc.User.Companion.toUser
import io.ktor.http.Parameters
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction

internal inline class ClassCode(val value: String) {
  val isTeacherMode get() = !isStudentMode
  val isStudentMode get() = value == CLASSES_DISABLED || value.isBlank()

  private val classCodeEnrollmentKey get() = listOf(CLASS_CODE_KEY, value).joinToString(KEY_SEP)

  val classInfoKey get() = listOf(CLASS_INFO_KEY, value).joinToString(KEY_SEP)

  fun isValid(redis: Jedis) = redis.exists(classCodeEnrollmentKey) ?: false

  fun isNotValid(redis: Jedis) = !isValid(redis)

  fun fetchEnrollees(redis: Jedis?): List<User> =
    if (redis.isNull() || isStudentMode)
      emptyList()
    else
      (redis.smembers(classCodeEnrollmentKey) ?: emptySet())
        .filter { it.isNotEmpty() }
        .map { it.toUser() }

  fun isEnrolled(user: User, redis: Jedis) = redis.sismember(classCodeEnrollmentKey, user.id) ?: false

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

  fun fetchClassDesc(redis: Jedis) = redis.hget(classInfoKey, DESC_FIELD) ?: "Missing description"

  fun fetchClassTeacherId(redis: Jedis) = redis.hget(classInfoKey, TEACHER_FIELD) ?: ""

  override fun toString() = value

  companion object {
    internal val STUDENT_CLASS_CODE = ClassCode("")

    internal fun newClassCode() = ClassCode(randomId(15))

    internal fun Parameters.getClassCode(parameterName: String) =
      this[parameterName]?.let { ClassCode(it) } ?: STUDENT_CLASS_CODE
  }
}