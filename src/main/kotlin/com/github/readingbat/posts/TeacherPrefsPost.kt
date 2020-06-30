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
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.*
import com.github.readingbat.misc.ClassCode.Companion.STUDENT_CLASS_CODE
import com.github.readingbat.misc.ClassCode.Companion.getClassCode
import com.github.readingbat.misc.ClassCode.Companion.newClassCode
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CLASS_DESC
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.User.Companion.fetchActiveClassCode
import com.github.readingbat.misc.User.Companion.fetchPreviousTeacherClassCode
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.call
import io.ktor.request.receiveParameters
import redis.clients.jedis.Jedis

internal object TeacherPrefsPost {
  private const val STUDENT_MODE_ENABLED_MSG = "Student mode enabled"
  private const val TEACHER_MODE_ENABLED_MSG = "Teacher mode enabled"

  suspend fun PipelineCall.teacherPrefs(content: ReadingBatContent, user: User?, redis: Jedis) =
    if (user.isValidUser(redis)) {
      val parameters = call.receiveParameters()
      when (val action = parameters[USER_PREFS_ACTION] ?: "") {
        CREATE_CLASS -> createClass(content, user, parameters[CLASS_DESC] ?: "", redis)
        UPDATE_ACTIVE_CLASS -> updateActiveClass(content, user, parameters.getClassCode(CLASSES_CHOICE), redis)
        DELETE_CLASS -> deleteClass(content, user, parameters.getClassCode(CLASS_CODE), redis)
        else -> throw InvalidConfigurationException("Invalid action: $action")
      }
    }
    else {
      requestLogInPage(content, redis)
    }

  fun PipelineCall.enableStudentMode(user: User?, redis: Jedis): String {
    val returnPath = queryParam(Constants.RETURN_PATH, "/")

    val msg =
      if (user.isValidUser(redis)) {
        user.assignActiveClassCode(STUDENT_CLASS_CODE, false, redis)
        STUDENT_MODE_ENABLED_MSG
      }
      else {
        "Invalid user"
      }
    throw RedirectException("$returnPath?$MSG=${msg.encode()}")
  }

  fun PipelineCall.enableTeacherMode(user: User?, redis: Jedis): String {
    val returnPath = queryParam(Constants.RETURN_PATH, "/")

    val msg =
      if (user.isValidUser(redis)) {
        val lastTeacherClassCode = user.fetchPreviousTeacherClassCode(redis)
        user.assignActiveClassCode(lastTeacherClassCode, false, redis)
        TEACHER_MODE_ENABLED_MSG
      }
      else {
        "Invalid user"
      }
    throw RedirectException("$returnPath?$MSG=${msg.encode()}")
  }

  private fun PipelineCall.createClass(content: ReadingBatContent,
                                       user: User,
                                       classDesc: String,
                                       redis: Jedis) =
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
                         Message("Exceeds maximum number classes [${content.maxClassCount}]", true),
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
                                             redis: Jedis): String {
    val activeClassCode = user.fetchActiveClassCode(redis)
    val msg =
      when {
        // Do not allow this for classCode.isStudentMode because turns off the
        // student/teacher toggle mode
        activeClassCode == classCode && classCode.isTeacherMode -> {
          Message("Same active class selected [$classCode]", true)
        }
        else -> {
          user.assignActiveClassCode(classCode, true, redis)
          if (classCode.isStudentMode)
            Message(STUDENT_MODE_ENABLED_MSG)
          else {
            Message("Active class updated to ${classCode.fetchClassDesc(redis)} [$classCode]")
          }
        }
      }

    return teacherPrefsPage(content, user, redis, msg)
  }

  private fun PipelineCall.deleteClass(content: ReadingBatContent,
                                       user: User,
                                       classCode: ClassCode,
                                       redis: Jedis) =
    when {
      classCode.isStudentMode -> {
        teacherPrefsPage(content, user, redis, Message("Empty class code", true))
      }
      classCode.isNotValid(redis) -> {
        teacherPrefsPage(content, user, redis, Message("Invalid class code: $classCode", true))
      }
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