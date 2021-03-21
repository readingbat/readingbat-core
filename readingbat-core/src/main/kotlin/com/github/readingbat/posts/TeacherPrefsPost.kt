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
import com.github.readingbat.common.ClassCode
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
import com.github.readingbat.common.FormFields.MAKE_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.github.readingbat.common.FormFields.REMOVE_FROM_CLASS
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.TEACHER_PREF
import com.github.readingbat.common.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.USER_ID_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.common.User.Companion.queryPreviousTeacherClassCode
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.common.isValidUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ClassSummaryPage.classSummaryPage
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import io.ktor.request.*
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction

internal object TeacherPrefsPost : KLogging() {
  private const val STUDENT_MODE_ENABLED_MSG = "Student mode enabled"
  private const val TEACHER_MODE_ENABLED_MSG = "Teacher mode enabled"

  suspend fun PipelineCall.teacherPrefs(content: ReadingBatContent, user: User?) =
    if (user.isValidUser()) {
      val params = call.receiveParameters()
      when (val action = params[PREFS_ACTION_PARAM] ?: "") {
        CREATE_CLASS -> createClass(content, user, params[CLASS_DESC_PARAM] ?: "")
        UPDATE_ACTIVE_CLASS,
        MAKE_ACTIVE_CLASS -> {
          val source = params[CHOICE_SOURCE_PARAM] ?: ""
          val classCode = params.getClassCode(CLASS_CODE_CHOICE_PARAM)
          val msg = updateActiveClass(user, classCode)
          when (source) {
            TEACHER_PREF -> teacherPrefsPage(content, user, msg)
            CLASS_SUMMARY -> classSummaryPage(content, user, classCode, msg = msg)
            else -> error("Invalid source: $source")
          }
        }
        REMOVE_FROM_CLASS -> {
          val studentId = params[USER_ID_PARAM] ?: error("Missing: $USER_ID_PARAM")
          val student = studentId.toUser()
          val classCode = student.enrolledClassCode
          student.withdrawFromClass(classCode)
          val msg = "${student.fullName} removed from class ${classCode.toDisplayString()}"
          logger.info { msg }
          classSummaryPage(content, user, classCode, msg = Message(msg))
        }
        DELETE_CLASS -> deleteClass(content, user, params.getClassCode(CLASS_CODE_NAME_PARAM))
        else -> error("Invalid action: $action")
      }
    }
    else {
      requestLogInPage(content)
    }

  fun PipelineCall.enableStudentMode(user: User?): String {
    val returnPath = queryParam(RETURN_PARAM, "/")
    //val browserSession = call.browserSession
    val msg =
      if (user.isValidUser()) {
        user.assignActiveClassCode(DISABLED_CLASS_CODE, false)
        STUDENT_MODE_ENABLED_MSG
      }
      else {
        "Invalid user"
      }
    throw RedirectException("$returnPath?$MSG=${msg.encode()}")
  }

  fun PipelineCall.enableTeacherMode(user: User?): String {
    val returnPath = queryParam(RETURN_PARAM, "/")
    val msg =
      if (user.isValidUser()) {
        val previousTeacherClassCode = queryPreviousTeacherClassCode(user)
        user.assignActiveClassCode(previousTeacherClassCode, false)
        TEACHER_MODE_ENABLED_MSG
      }
      else {
        "Invalid user"
      }
    throw RedirectException("$returnPath?$MSG=${msg.encode()}")
  }

  private fun PipelineCall.createClass(content: ReadingBatContent, user: User, classDesc: String) =
    when {
      classDesc.isBlank() -> {
        teacherPrefsPage(content, user, Message("Unable to create class [Empty class description]", true))
      }
      !user.isUniqueClassDesc(classDesc) -> {
        teacherPrefsPage(content, user, Message("Class description is not unique [$classDesc]", true), classDesc)
      }
      user.classCount() == content.maxClassCount -> {
        val msg = Message("Maximum number of classes is: [${content.maxClassCount}]", true)
        teacherPrefsPage(content, user, msg, classDesc)
      }
      else -> {
        // Add classcode to list of classes created by user
        val classCode = newClassCode()
        user.addClassCode(classCode, classDesc)
        teacherPrefsPage(content, user, Message("Created class code: $classCode", false))
      }
    }

  private fun updateActiveClass(user: User, classCode: ClassCode) =
    when {
      // Do not allow this for classCode.isStudentMode because turns off the
      // student/teacher toggle mode
      queryActiveTeachingClassCode(user) == classCode && classCode.isEnabled ->
        Message("Same active class selected [$classCode]", true)
      else -> {
        user.assignActiveClassCode(classCode, true)
        Message(
          if (classCode.isNotEnabled)
            STUDENT_MODE_ENABLED_MSG
          else
            "Active class updated to ${classCode.toDisplayString()}")
      }
    }

  private fun PipelineCall.deleteClass(content: ReadingBatContent, user: User, classCode: ClassCode) =
    when {
      classCode.isNotEnabled -> teacherPrefsPage(content, user, Message("Empty class code", true))
      classCode.isNotValid() -> teacherPrefsPage(content,
                                                 user,
                                                 Message("Invalid class code: $classCode", true))
      else -> {
        val activeTeachingClassCode = queryActiveTeachingClassCode(user)
        val enrollees = classCode.fetchEnrollees()

        transaction {
          if (activeTeachingClassCode == classCode)
            user.resetActiveClassCode()

          user.unenrollEnrolleesClassCode(classCode, enrollees)
          classCode.deleteClassCode()
        }

        teacherPrefsPage(content, user, Message("Deleted class code: $classCode"))
      }
    }
}