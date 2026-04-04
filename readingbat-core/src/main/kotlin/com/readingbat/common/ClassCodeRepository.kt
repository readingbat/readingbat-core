/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.common

import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import com.pambrose.common.util.toDoubleQuoted
import com.readingbat.common.User.Companion.toUser
import com.readingbat.server.ClassesTable
import com.readingbat.server.EnrolleesTable
import com.readingbat.server.UsersTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.measureTimedValue

/**
 * Repository for class code database operations, providing extension functions on [ClassCode].
 *
 * Handles persistence and lookup of class codes, including validation, enrollee management,
 * and teacher association queries. All operations interact with [ClassesTable] and [EnrolleesTable].
 */
object ClassCodeRepository {
  private val logger = KotlinLogging.logger {}

  private fun lookupClassCodeDbmsId(classCode: ClassCode): Long =
    measureTimedValue {
      readonlyTx {
        with(ClassesTable) {
          select(id)
            .where { this.classCode eq classCode.classCode }
            .map { it[id].value }
            .firstOrNull() ?: error("Missing class code ${classCode.classCode}")
        }
      }
    }.let {
      logger.debug { "Looked up classId in ${it.duration}" }
      it.value
    }

  /** Returns true if this class code exists in the database. */
  fun ClassCode.isValid() =
    readonlyTx {
      with(ClassesTable) {
        select(Count(classCode))
          .where { classCode eq this@isValid.classCode }
          .map { it[0] as Long }
          .first()
          .also { logger.debug { "ClassCode.isValid() returned $it for ${this@isValid.classCode}" } } > 0
      }
    }

  fun ClassCode.isNotValid() = !isValid()

  /** Returns all users enrolled in the class identified by this class code. */
  fun ClassCode.fetchEnrollees(): List<User> =
    if (isNotEnabled) {
      emptyList()
    } else {
      readonlyTx {
        val userIds =
          (ClassesTable innerJoin EnrolleesTable)
            .select(EnrolleesTable.userRef)
            .where { ClassesTable.classCode eq classCode }
            .map { it[0] as Long }

        with(UsersTable) {
          selectAll()
            .where { id inList userIds }
            .map { it[userId].toUser(it) }
            .also { logger.debug { "fetchEnrollees() returning ${it.size} users" } }
        }
      }
    }

  /** Deletes this class code from the database. */
  fun ClassCode.deleteClassCode() =
    with(ClassesTable) {
      deleteWhere { classCode eq this@deleteClassCode.classCode }
    }

  /** Adds the given user as an enrollee in this class. */
  fun ClassCode.addEnrollee(user: User) =
    transaction {
      val dbmsId = lookupClassCodeDbmsId(this@addEnrollee)
      with(EnrolleesTable) {
        insert { row ->
          row[classesRef] = dbmsId
          row[userRef] = user.userDbmsId
        }
      }
    }

  /** Removes the given user from this class's enrollee list. */
  fun ClassCode.removeEnrollee(user: User) {
    val dbmsId = lookupClassCodeDbmsId(this)
    with(EnrolleesTable) {
      deleteWhere { (classesRef eq dbmsId) and (userRef eq user.userDbmsId) }
    }
  }

  /** Fetches the human-readable description for this class code, optionally double-quoted. */
  fun ClassCode.fetchClassDesc(quoted: Boolean = false) =
    readonlyTx {
      with(ClassesTable) {
        select(description)
          .where { classCode eq this@fetchClassDesc.classCode }
          .map { it[0] as String }
          .firstOrNull() ?: "Missing description"
      }.also {
        logger.debug { "fetchClassDesc() returned ${it.toDoubleQuoted()} for ${this@fetchClassDesc.classCode}" }
      }
    }.let { if (quoted) it.toDoubleQuoted() else it }

  fun ClassCode.toDisplayString() = "${fetchClassDesc(true)} [$classCode]"

  /** Returns the userId of the teacher who owns this class, or empty string if not found. */
  fun ClassCode.fetchClassTeacherId() =
    readonlyTx {
      (
        (ClassesTable innerJoin UsersTable)
          .select(UsersTable.userId)
          .where { ClassesTable.classCode eq this@fetchClassTeacherId.classCode }
          .map { it[0] as String }
          .firstOrNull() ?: ""
        ).also { logger.debug { "fetchClassTeacherId() returned $it" } }
    }
}
