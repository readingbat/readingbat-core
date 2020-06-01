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

import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.ClassCode.Companion.classCodeFromParameter
import com.github.readingbat.misc.ClassCode.Companion.newClassCode
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CLASS_DESC
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.User
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserPrefsPage.fetchClassDesc
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import io.ktor.application.call
import io.ktor.request.receiveParameters
import redis.clients.jedis.Jedis

internal object TeacherPrefs {
  suspend fun PipelineCall.teacherPrefs(content: ReadingBatContent, redis: Jedis): String {
    val parameters = call.receiveParameters()
    val principal = fetchPrincipal()
    val user = principal?.toUser()

    return if (user == null) {
      requestLogInPage(content, redis)
    }
    else {
      when (val action = parameters[USER_PREFS_ACTION] ?: "") {
        CREATE_CLASS -> createClass(content, redis, user, parameters[CLASS_DESC] ?: "")
        UPDATE_ACTIVE_CLASS -> updateActiveClass(content,
                                                 redis,
                                                 user,
                                                 classCodeFromParameter(parameters, CLASSES_CHOICE))
        DELETE_CLASS -> deleteClass(content, redis, user, classCodeFromParameter(parameters, CLASS_CODE))
        else -> throw InvalidConfigurationException("Invalid action: $action")
      }
    }
  }

  private fun PipelineCall.createClass(content: ReadingBatContent,
                                       redis: Jedis,
                                       user: User,
                                       classDesc: String) =
    when {
      classDesc.isBlank() -> {
        teacherPrefsPage(content, redis, "Unable to create class [Empty class description]", true)
      }
      !user.isUniqueClassDesc(classDesc, redis) -> {
        teacherPrefsPage(content, redis, "Class description is not unique [$classDesc]", true, classDesc)
      }
      user.classCount(redis) == content.maxClassCount -> {
        teacherPrefsPage(content, redis, "Exceeds maximum number classes [${content.maxClassCount}]", true, classDesc)
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
        teacherPrefsPage(content, redis, "Created class code: $classCode", false)
      }
    }

  private fun PipelineCall.updateActiveClass(content: ReadingBatContent,
                                             redis: Jedis,
                                             user: User,
                                             classCode: ClassCode): String {
    val activeClassCode = user.fetchActiveClassCode(redis)
    val msg =
      when {
        activeClassCode.isNotEmpty && classCode.isClassesDisabled -> {
          "Active class disabled"
        }
        activeClassCode == classCode -> {
          "Same active class selected"
        }
        else -> {
          user.assignActiveClassCode(classCode, redis)
          if (classCode.isClassesDisabled)
            "Active class disabled"
          else
            "Active class updated to: $classCode [${fetchClassDesc(classCode, redis)}]"
        }
      }

    return teacherPrefsPage(content, redis, msg, false)
  }

  private fun PipelineCall.deleteClass(content: ReadingBatContent,
                                       redis: Jedis,
                                       user: User,
                                       classCode: ClassCode) =
    when {
      classCode.isNotEmpty -> {
        teacherPrefsPage(content, redis, "Empty class code", true)
      }
      classCode.isNotValid(redis) -> {
        teacherPrefsPage(content, redis, "Invalid class code: $classCode", true)
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

        teacherPrefsPage(content, redis, "Deleted class code: $classCode", false)
      }
    }
}