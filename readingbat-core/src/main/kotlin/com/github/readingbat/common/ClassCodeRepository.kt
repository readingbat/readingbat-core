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

import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.server.ClassesTable
import com.github.readingbat.server.EnrolleesTable
import com.github.readingbat.server.UsersTable
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
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

  fun ClassCode.deleteClassCode() =
    with(ClassesTable) {
      deleteWhere { classCode eq this@deleteClassCode.classCode }
    }

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

  fun ClassCode.removeEnrollee(user: User) {
    val dbmsId = lookupClassCodeDbmsId(this)
    with(EnrolleesTable) {
      deleteWhere { (classesRef eq dbmsId) and (userRef eq user.userDbmsId) }
    }
  }

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
