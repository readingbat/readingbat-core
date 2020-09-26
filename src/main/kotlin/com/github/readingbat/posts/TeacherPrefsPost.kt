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

package com.github.readingbat.posts

import com.github.pambrose.common.util.encode
import com.github.readingbat.common.*
import com.github.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.common.ClassCode.Companion.getClassCode
import com.github.readingbat.common.ClassCode.Companion.newClassCode
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.FormFields.CHOICE_SOURCE_PARAM
import com.github.readingbat.common.FormFields.CLASS_CODE_CHOICE_PARAM
import com.github.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.github.readingbat.common.FormFields.CLASS_DESC_PARAM
import com.github.readingbat.common.FormFields.CLASS_SUMMARY
import com.github.readingbat.common.FormFields.CREATE_CLASS
import com.github.readingbat.common.FormFields.DELETE_CLASS
import com.github.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.github.readingbat.common.FormFields.REMOVE_FROM_CLASS
import com.github.readingbat.common.FormFields.TEACHER_PREF
import com.github.readingbat.common.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.USER_ID_PARAM
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.fetchPreviousTeacherClassCode
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ClassSummaryPage.classSummaryPage
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ReadingBatServer.usePostgres
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import io.ktor.request.*
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.Jedis

internal object TeacherPrefsPost : KLogging() {
  private const val STUDENT_MODE_ENABLED_MSG = "Student mode enabled"
  private const val TEACHER_MODE_ENABLED_MSG = "Teacher mode enabled"

  suspend fun PipelineCall.teacherPrefs(content: ReadingBatContent, user: User?, redis: Jedis) =
    if (user.isValidUser(redis)) {
      val parameters = call.receiveParameters()
      when (val action = parameters[PREFS_ACTION_PARAM] ?: "") {
        CREATE_CLASS -> createClass(content, user, parameters[CLASS_DESC_PARAM] ?: "", redis)
        UPDATE_ACTIVE_CLASS -> {
          val source = parameters[CHOICE_SOURCE_PARAM] ?: ""
          val classCode = parameters.getClassCode(CLASS_CODE_CHOICE_PARAM)
          val msg = updateActiveClass(user, classCode, redis)
          when (source) {
            TEACHER_PREF -> teacherPrefsPage(content, user, redis, msg)
            CLASS_SUMMARY -> classSummaryPage(content, user, redis, classCode, msg = msg)
            else -> throw InvalidConfigurationException("Invalid source: $source")
          }
        }
        REMOVE_FROM_CLASS -> {
          val studentId = parameters[USER_ID_PARAM] ?: throw InvalidConfigurationException("Missing: $USER_ID_PARAM")
          val student = studentId.toUser(redis, null)
          val classCode = student.enrolledClassCode
          student.withdrawFromClass(classCode, redis)
          val msg = "${student.name} removed from class ${classCode.toDisplayString(redis)}"
          logger.info { msg }
          classSummaryPage(content, user, redis, classCode, msg = Message(msg))
        }
        DELETE_CLASS -> deleteClass(content, user, parameters.getClassCode(CLASS_CODE_NAME_PARAM), redis)
        else -> throw InvalidConfigurationException("Invalid action: $action")
      }
    }
    else {
      requestLogInPage(content, redis)
    }

  fun PipelineCall.enableStudentMode(user: User?, redis: Jedis): String {
    val returnPath = queryParam(FormFields.RETURN_PARAM, "/")
    val browserSession = call.browserSession
    val msg =
      if (user.isValidUser(redis)) {
        user.assignActiveClassCode(DISABLED_CLASS_CODE, false, redis)
        STUDENT_MODE_ENABLED_MSG
      }
      else {
        "Invalid user"
      }
    throw RedirectException("$returnPath?$MSG=${msg.encode()}")
  }

  fun PipelineCall.enableTeacherMode(user: User?, redis: Jedis): String {
    val returnPath = queryParam(FormFields.RETURN_PARAM, "/")
    val msg =
      if (user.isValidUser(redis)) {
        val previousTeacherClassCode = fetchPreviousTeacherClassCode(user, redis)
        user.assignActiveClassCode(previousTeacherClassCode, false, redis)
        TEACHER_MODE_ENABLED_MSG
      }
      else {
        "Invalid user"
      }
    throw RedirectException("$returnPath?$MSG=${msg.encode()}")
  }

  private fun PipelineCall.createClass(content: ReadingBatContent, user: User, classDesc: String, redis: Jedis) =
    when {
      classDesc.isBlank() -> {
        teacherPrefsPage(content, user, redis, Message("Unable to create class [Empty class description]", true))
      }
      !user.isUniqueClassDesc(classDesc, redis) -> {
        teacherPrefsPage(content, user, redis, Message("Class description is not unique [$classDesc]", true), classDesc)
      }
      user.classCount(redis) == content.maxClassCount -> {
        val msg = Message("Maximum number of classes is: [${content.maxClassCount}]", true)
        teacherPrefsPage(content, user, redis, msg, classDesc)
      }
      else -> {
        // Add classcode to list of classes created by user
        val classCode = newClassCode()
        if (usePostgres)
          user.addClassCode(classCode, classDesc)
        else
          redis.multi()
            .also { tx ->
              user.addClassCode(classCode, classDesc, tx)
              tx.exec()
            }
        teacherPrefsPage(content, user, redis, Message("Created class code: $classCode", false))
      }
    }

  private fun updateActiveClass(user: User, classCode: ClassCode, redis: Jedis) =
    when {
      // Do not allow this for classCode.isStudentMode because turns off the
      // student/teacher toggle mode
      fetchActiveClassCode(user, redis) == classCode && classCode.isEnabled ->
        Message("Same active class selected [$classCode]", true)
      else -> {
        user.assignActiveClassCode(classCode, true, redis)
        Message(
          if (classCode.isNotEnabled)
            STUDENT_MODE_ENABLED_MSG
          else
            "Active class updated to ${classCode.toDisplayString(redis)}")
      }
    }

  private fun PipelineCall.deleteClass(content: ReadingBatContent,
                                       user: User,
                                       classCode: ClassCode,
                                       redis: Jedis) =
    when {
      classCode.isNotEnabled -> teacherPrefsPage(content, user, redis, Message("Empty class code", true))
      classCode.isNotValid(redis) -> teacherPrefsPage(content,
                                                      user,
                                                      redis,
                                                      Message("Invalid class code: $classCode", true))
      else -> {
        val activeClassCode = fetchActiveClassCode(user, redis)
        val enrollees = classCode.fetchEnrollees(redis)

        if (usePostgres) {
          transaction {
            if (activeClassCode == classCode)
              user.resetActiveClassCode()

            user.unenrollEnrolleesClassCode(classCode, enrollees)
            classCode.deleteClassCode()
          }
        }
        else {
          redis.multi()
            .also { tx ->
              // Disable current class if deleted class is the active class
              if (activeClassCode == classCode)
                user.resetActiveClassCode(tx)

              user.deleteClassCode(classCode, enrollees, tx)

              tx.exec()
            }
        }

        teacherPrefsPage(content, user, redis, Message("Deleted class code: $classCode"))
      }
    }
}