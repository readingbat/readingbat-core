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

import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.common.FormFields.DISABLED_MODE
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.server.Classes
import com.github.readingbat.server.Enrollees
import com.github.readingbat.server.Users
import com.github.readingbat.server.get
import io.ktor.http.*
import mu.KLogging
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.measureTimedValue

internal data class ClassCode(val value: String) {
  val isNotEnabled by lazy { value == DISABLED_MODE || value.isBlank() }
  val isEnabled by lazy { !isNotEnabled }

  val displayedValue get() = if (value == DISABLED_MODE) "" else value

  private val classCodeDbmsId
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
        logger.debug { "Looked up classId in ${it.duration}" }
        it.value
      }

  fun isNotValid() = !isValid()

  fun isValid() =
    transaction {
      Classes
        .slice(Count(Classes.classCode))
        .select { Classes.classCode eq value }
        .map { it[0] as Long }
        .first().also { logger.debug { "ClassCode.isValid() returned $it for $value" } } > 0
    }

  fun fetchEnrollees(): List<User> =
    if (isNotEnabled)
      emptyList()
    else
      transaction {
        val userIds =
          (Classes innerJoin Enrollees)
            .slice(Enrollees.userRef)
            .select { Classes.classCode eq value }
            .map { it[0] as Long }

        Users
          .select { Users.id inList userIds }
          .map { toUser(it[Users.userId], it) }
          .also { logger.debug { "fetchEnrollees() returning ${it.size} users" } }
      }

  fun deleteClassCode() = Classes.deleteWhere { Classes.classCode eq value }

  fun addEnrollee(user: User) =
    transaction {
      Enrollees
        .insert { row ->
          row[classesRef] = classCodeDbmsId
          row[userRef] = user.userDbmsId
        }
    }

  fun removeEnrollee(user: User) =
    Enrollees.deleteWhere { (Enrollees.classesRef eq classCodeDbmsId) and (Enrollees.userRef eq user.userDbmsId) }


  fun fetchClassDesc(quoted: Boolean = false) =
    transaction {
      (Classes
        .slice(Classes.description)
        .select { Classes.classCode eq value }
        .map { it[0] as String }
        .firstOrNull() ?: "Missing description")
        .also { logger.debug { "fetchClassDesc() returned ${it.toDoubleQuoted()} for $value" } }
    }.let { if (quoted) it.toDoubleQuoted() else it }

  fun toDisplayString() = "${fetchClassDesc(true)} [$value]"

  fun fetchClassTeacherId() =
    transaction {
      ((Classes innerJoin Users)
        .slice(Users.userId)
        .select { Classes.classCode eq value }
        .map { it[0] as String }
        .firstOrNull() ?: "").also { logger.debug { "fetchClassTeacherId() returned $it" } }
    }

  override fun toString() = value

  companion object : KLogging() {
    internal val DISABLED_CLASS_CODE = ClassCode(DISABLED_MODE)

    internal fun newClassCode() = ClassCode(randomId(15))

    internal fun Parameters.getClassCode(parameterName: String) =
      this[parameterName]?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
  }
}