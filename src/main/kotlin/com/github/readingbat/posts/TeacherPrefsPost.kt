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
import com.github.readingbat.common.FormFields.TEACHER_PREF
import com.github.readingbat.common.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.USER_PREFS_ACTION_PARAM
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.fetchPreviousTeacherClassCode
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ClassSummaryPage.classSummaryPage
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import io.ktor.request.*
import redis.clients.jedis.Jedis

internal object TeacherPrefsPost {
  private const val STUDENT_MODE_ENABLED_MSG = "Student mode enabled"
  private const val TEACHER_MODE_ENABLED_MSG = "Teacher mode enabled"

  suspend fun PipelineCall.teacherPrefs(content: ReadingBatContent, user: User?, redis: Jedis) =
    if (user.isValidUser(redis)) {
      val parameters = call.receiveParameters()
      when (val action = parameters[USER_PREFS_ACTION_PARAM] ?: "") {
        CREATE_CLASS -> createClass(content, user, parameters[CLASS_DESC_PARAM] ?: "", redis)
        UPDATE_ACTIVE_CLASS -> {
          val source = parameters[CHOICE_SOURCE_PARAM] ?: ""
          val classCode = parameters.getClassCode(CLASS_CODE_CHOICE_PARAM)
          val msg = updateActiveClass(content, user, classCode, redis)
          when (source) {
            TEACHER_PREF -> teacherPrefsPage(content, user, redis, msg)
            CLASS_SUMMARY -> classSummaryPage(content, user, redis, classCode, msg = msg)
            else -> throw InvalidConfigurationException("Invalid source: $source")
          }
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
        val previousTeacherClassCode = user.fetchPreviousTeacherClassCode(redis)
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
        teacherPrefsPage(content,
                         user,
                         redis,
                         Message("Maximum number of classes is: [${content.maxClassCount}]", true),
                         classDesc)
      }
      else -> {
        val classCode = newClassCode()

        redis.multi().also { tx ->

          classCode.initializeWith(classDesc, user, tx)

          // Add classcode to list of classes created by user
          user.addClassCreated(classCode, tx)

          // Create class with no one enrolled to prevent class from being created a 2nd time
          classCode.addEnrolleePlaceholder(tx)

          tx.exec()
        }
        teacherPrefsPage(content, user, redis, Message("Created class code: $classCode", false))
      }
    }

  private fun PipelineCall.updateActiveClass(content: ReadingBatContent,
                                             user: User,
                                             classCode: ClassCode,
                                             redis: Jedis) =
    when {
      // Do not allow this for classCode.isStudentMode because turns off the
      // student/teacher toggle mode
      user.fetchActiveClassCode(redis) == classCode &&
          classCode.isEnabled -> Message("Same active class selected [$classCode]", true)
      else -> {
        user.assignActiveClassCode(classCode, true, redis)
        if (classCode.isNotEnabled)
          Message(STUDENT_MODE_ENABLED_MSG)
        else {
          Message("Active class updated to ${classCode.toDisplayString(redis)}")
        }
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
        val activeClassCode = user.fetchActiveClassCode(redis)
        val enrollees = classCode.fetchEnrollees(redis)

        redis.multi().also { tx ->
          // Disable current class if deleted class is the active class
          if (activeClassCode == classCode)
            user.resetActiveClassCode(tx)

          user.deleteClassCode(classCode, enrollees, tx)

          tx.exec()
        }

        teacherPrefsPage(content, user, redis, Message("Deleted class code: $classCode"))
      }
    }
}