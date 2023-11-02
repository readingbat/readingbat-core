/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.common.FormFields.DISABLED_MODE
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.server.ClassesTable
import com.github.readingbat.server.EnrolleesTable
import com.github.readingbat.server.UsersTable
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import io.ktor.http.*
import mu.two.KLogging
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.measureTimedValue

data class ClassCode(val classCode: String) {
  val isNotEnabled by lazy { classCode == DISABLED_MODE || classCode.isBlank() }
  val isEnabled by lazy { !isNotEnabled }

  val displayedValue get() = if (classCode == DISABLED_MODE) "" else classCode

  private val classCodeDbmsId
    get() =
      measureTimedValue {
        readonlyTx {
          ClassesTable
            .slice(ClassesTable.id)
            .select { ClassesTable.classCode eq classCode }
            .map { it[ClassesTable.id].value }
            .firstOrNull() ?: error("Missing class code $classCode")
        }
      }.let {
        logger.debug { "Looked up classId in ${it.duration}" }
        it.value
      }

  fun isNotValid() = !isValid()

  fun isValid() =
    readonlyTx {
      ClassesTable
        .slice(Count(ClassesTable.classCode))
        .select { ClassesTable.classCode eq classCode }
        .map { it[0] as Long }
        .first().also { logger.debug { "ClassCode.isValid() returned $it for $classCode" } } > 0
    }

  fun fetchEnrollees(): List<User> =
    if (isNotEnabled)
      emptyList()
    else
      readonlyTx {
        val userIds =
          (ClassesTable innerJoin EnrolleesTable)
            .slice(EnrolleesTable.userRef)
            .select { ClassesTable.classCode eq classCode }
            .map { it[0] as Long }

        UsersTable
          .select { UsersTable.id inList userIds }
          .map { it[UsersTable.userId].toUser(it) }
          .also { logger.debug { "fetchEnrollees() returning ${it.size} users" } }
      }

  fun deleteClassCode() = ClassesTable.deleteWhere { classCode eq this@ClassCode.classCode }

  fun addEnrollee(user: User) =
    transaction {
      EnrolleesTable
        .insert { row ->
          row[classesRef] = classCodeDbmsId
          row[userRef] = user.userDbmsId
        }
    }

  fun removeEnrollee(user: User) =
    EnrolleesTable.deleteWhere { (classesRef eq this@ClassCode.classCodeDbmsId) and (userRef eq user.userDbmsId) }

  fun fetchClassDesc(quoted: Boolean = false) =
    readonlyTx {
      (
        ClassesTable
          .slice(ClassesTable.description)
          .select { ClassesTable.classCode eq classCode }
          .map { it[0] as String }
          .firstOrNull() ?: "Missing description"
        ).also { logger.debug { "fetchClassDesc() returned ${it.toDoubleQuoted()} for $classCode" } }
    }.let { if (quoted) it.toDoubleQuoted() else it }

  fun toDisplayString() = "${fetchClassDesc(true)} [$classCode]"

  fun fetchClassTeacherId() =
    readonlyTx {
      (
        (ClassesTable innerJoin UsersTable)
          .slice(UsersTable.userId)
          .select { ClassesTable.classCode eq classCode }
          .map { it[0] as String }
          .firstOrNull() ?: ""
        ).also { logger.debug { "fetchClassTeacherId() returned $it" } }
    }

  override fun toString() = classCode

  companion object : KLogging() {
    internal val DISABLED_CLASS_CODE = ClassCode(DISABLED_MODE)

    internal fun newClassCode() = ClassCode(randomId(15))

    internal fun Parameters.getClassCode(parameterName: String) =
      this[parameterName]?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
  }
}
