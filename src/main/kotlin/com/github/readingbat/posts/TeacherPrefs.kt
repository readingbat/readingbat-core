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

import com.github.pambrose.common.util.randomId
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASSES_DISABLED
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CLASS_DESC
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.KeyConstants.ACTIVE_CLASS_CODE_FIELD
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.classCodeEnrollmentKey
import com.github.readingbat.misc.UserId.Companion.classDescKey
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserPrefsPage.classDesc
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
    val userId = com.github.readingbat.misc.UserId.userIdByPrincipal(principal)

    return if (userId == null || principal == null) {
      requestLogInPage(content, redis)
    }
    else {
      when (val action = parameters[USER_PREFS_ACTION] ?: "") {
        CREATE_CLASS -> createClass(content, redis, userId, parameters[CLASS_DESC] ?: "")
        UPDATE_ACTIVE_CLASS -> updateActiveClass(content, redis, userId, parameters[CLASSES_CHOICE] ?: "")
        DELETE_CLASS -> deleteClass(content, redis, userId, parameters[CLASS_CODE] ?: "")
        else -> throw InvalidConfigurationException("Invalid action: $action")
      }
    }
  }

  private fun PipelineCall.createClass(content: ReadingBatContent,
                                       redis: Jedis,
                                       userId: UserId,
                                       classDesc: String) =
    when {
      classDesc.isBlank() -> {
        teacherPrefsPage(content, redis, "Unable to create class [Empty class description]", true)
      }
      !userId.isUniqueClassDesc(classDesc, redis) -> {
        teacherPrefsPage(content, redis, "Class description is not unique [$classDesc]", true, classDesc)
      }
      userId.classCount(redis) == content.maxClassCount -> {
        teacherPrefsPage(content, redis, "Exceeds maximum number classes [${content.maxClassCount}]", true, classDesc)
      }
      else -> {
        val classCode = randomId(15)

        redis.multi().also { tx ->
          // Create KV for class description
          tx.set(classDescKey(classCode), classDesc)

          // Add classcode to list of classes created by user
          tx.sadd(userId.userClassesKey, classCode)

          // Create class with no one enrolled to prevent class from being created a 2nd time
          val classCodeEnrollmentKey = classCodeEnrollmentKey(classCode)
          tx.sadd(classCodeEnrollmentKey, "")

          tx.exec()
        }
        teacherPrefsPage(content, redis, "Created class code: $classCode", false)
      }
    }

  private fun PipelineCall.updateActiveClass(content: ReadingBatContent,
                                             redis: Jedis,
                                             userId: UserId,
                                             classCode: String): String {
    val activeClassCode = userId.fetchActiveClassCode(redis)
    val msg =
      when {
        activeClassCode.isEmpty() && classCode == CLASSES_DISABLED -> {
          "Active class disabled"
        }
        activeClassCode == classCode -> {
          "Same active class selected"
        }
        else -> {
          redis.hset(userId.userInfoKey, ACTIVE_CLASS_CODE_FIELD, if (classCode == CLASSES_DISABLED) "" else classCode)
          if (classCode == CLASSES_DISABLED)
            "Active class disabled"
          else
            "Active class updated to: $classCode [${classDesc(classCode, redis)}]"
        }
      }

    return teacherPrefsPage(content, redis, msg, false)
  }

  private fun PipelineCall.deleteClass(content: ReadingBatContent,
                                       redis: Jedis,
                                       userId: UserId,
                                       classCode: String) =
    when {
      classCode.isBlank() -> {
        teacherPrefsPage(content, redis, "Empty class code", true)
      }
      !redis.exists(classCodeEnrollmentKey(classCode)) -> {
        teacherPrefsPage(content, redis, "Invalid class code: $classCode", true)
      }
      else -> {
        val classCodeEnrollmentKey = classCodeEnrollmentKey(classCode)
        val activeClassCode = userId.fetchActiveClassCode(redis)
        val enrollees = redis.smembers(classCodeEnrollmentKey)

        redis.multi().also { tx ->
          // Disable current class if deleted class is the active class
          if (activeClassCode == classCode)
            tx.hset(userId.userInfoKey, ACTIVE_CLASS_CODE_FIELD, "")

          // Delete KV for class description
          tx.del(classDescKey(classCode), classCode)

          // Remove classcode from list of classes created by user
          tx.srem(userId.userClassesKey, classCode)

          // Reset every enrollee's enrolled class
          enrollees
            .map { UserId(it) }
            .forEach {
              it.assignEnrolledClassCode("", tx)
            }

          // Delete enrollee list
          tx.del(classCodeEnrollmentKey)

          tx.exec()
        }

        teacherPrefsPage(content, redis, "Deleted class code: $classCode", false)
      }
    }
}