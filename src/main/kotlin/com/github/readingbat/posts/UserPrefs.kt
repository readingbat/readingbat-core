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

import com.github.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASSES_DISABLED
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CLASS_DESC
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.CURR_PASSWORD
import com.github.readingbat.misc.FormFields.DELETE_ACCOUNT
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.JOIN_CLASS
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.UPDATE_CLASS
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.KeyConstants.ACTIVE_CLASS_CODE_FIELD
import com.github.readingbat.misc.KeyConstants.DIGEST_FIELD
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.classCodeEnrollmentKey
import com.github.readingbat.misc.UserId.Companion.classDescKey
import com.github.readingbat.misc.UserId.Companion.lookupDigestInfoByUserId
import com.github.readingbat.misc.UserId.Companion.userIdByPrincipal
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.CreateAccount.checkPassword
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import io.ktor.application.call
import io.ktor.http.Parameters
import io.ktor.request.receiveParameters
import io.ktor.sessions.clear
import io.ktor.sessions.sessions
import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisException

internal object UserPrefs : KLogging() {

  suspend fun PipelineCall.userPrefs(content: ReadingBatContent) =
    withSuspendingRedisPool { redis ->
      val parameters = call.receiveParameters()

      if (redis == null) {
        userPrefsPage(content, DBMS_DOWN, true)
      }
      else {
        val principal = fetchPrincipal()
        val userId = userIdByPrincipal(principal)
        if (userId == null || principal == null) {
          requestLogInPage(content)
        }
        else {
          when (val action = parameters[USER_PREFS_ACTION] ?: "") {
            UPDATE_PASSWORD -> updatePassword(content, parameters, userId, redis)
            JOIN_CLASS -> joinClass(content, parameters, userId, redis)
            CREATE_CLASS -> createClass(content, userId, redis, parameters[CLASS_DESC] ?: "")
            UPDATE_CLASS -> updateActiveClass(content, userId, redis, parameters[CLASSES_CHOICE] ?: "")
            DELETE_CLASS -> deleteClass(content, userId, redis, parameters[CLASS_CODE] ?: "")
            DELETE_ACCOUNT -> deleteAccount(content, principal, userId, redis)
            else -> throw InvalidConfigurationException("Invalid action: $action")
          }
        }
      }
    }

  private fun PipelineCall.updatePassword(content: ReadingBatContent,
                                          parameters: Parameters,
                                          userId: UserId,
                                          redis: Jedis): String {
    val currPassword = parameters[CURR_PASSWORD] ?: ""
    val newPassword = parameters[NEW_PASSWORD] ?: ""
    val confirmPassword = parameters[CONFIRM_PASSWORD] ?: ""
    val passwordError = checkPassword(newPassword, confirmPassword)

    val msg =
      if (passwordError.isNotEmpty()) {
        passwordError to true
      }
      else {
        val (salt, digest) = lookupDigestInfoByUserId(userId, redis)
        if (salt.isNotEmpty() && digest.isNotEmpty() && digest == currPassword.sha256(salt)) {
          val newDigest = newPassword.sha256(salt)
          redis.hset(userId.userInfoKey, DIGEST_FIELD, newDigest)
          "Password changed" to false
        }
        else {
          "Incorrect current password" to true
        }
      }

    return userPrefsPage(content, msg.first, msg.second)
  }

  private fun PipelineCall.joinClass(content: ReadingBatContent,
                                     parameters: Parameters,
                                     userId: UserId,
                                     redis: Jedis): String {
    val classCode = parameters[CLASS_CODE] ?: ""
    return try {
      userId.enrollIntoClass(classCode, redis)
      userPrefsPage(content, "Enrolled in class $classCode", false)
    } catch (e: JedisException) {
      logger.info { e }
      userPrefsPage(content,
                    "Unable to enroll in class [${e.message ?: ""}]",
                    true,
                    defaultClassCode = classCode)
    }
  }

  private fun PipelineCall.createClass(content: ReadingBatContent,
                                       userId: UserId,
                                       redis: Jedis,
                                       classDesc: String) =
    if (classDesc.isBlank()) {
      userPrefsPage(content, "Empty class description", true)
    }
    else {
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
      userPrefsPage(content, "Created class code: $classCode", false)
    }

  private fun PipelineCall.updateActiveClass(content: ReadingBatContent,
                                             userId: UserId,
                                             redis: Jedis,
                                             classCode: String): String {
    val activeClassCode = redis.hget(userId.userInfoKey, ACTIVE_CLASS_CODE_FIELD)
    val msg =
      if ((activeClassCode.isEmpty() && classCode == CLASSES_DISABLED) || activeClassCode == classCode) {
        "Same active class selected"
      }
      else {
        redis.hset(userId.userInfoKey, ACTIVE_CLASS_CODE_FIELD, if (classCode == CLASSES_DISABLED) "" else classCode)
        if (classCode == CLASSES_DISABLED)
          "Current active class disabled"
        else
          "Current active class updated to: $classCode [${redis[classDescKey(classCode)] ?: "Missing Description"}]"
      }

    return userPrefsPage(content, msg, false)
  }

  private fun PipelineCall.deleteClass(content: ReadingBatContent,
                                       userId: UserId,
                                       redis: Jedis,
                                       classCode: String) =
    if (classCode.isBlank()) {
      userPrefsPage(content, "Empty class code", true)
    }
    else if (!redis.exists(classCodeEnrollmentKey(classCode))) {
      userPrefsPage(content, "Invalid class code: $classCode", true)
    }
    else {
      redis.multi().also { tx ->
        // Delete KV for class description
        tx.del(classDescKey(classCode), classCode)

        // Remove classcode from list of classes created by user
        tx.srem(userId.userClassesKey, classCode)

        // Delete enrollees
        tx.del(classCodeEnrollmentKey(classCode))

        tx.exec()
      }

      userPrefsPage(content, "Deleted class code: $classCode", false)
    }

  private fun PipelineCall.deleteAccount(content: ReadingBatContent,
                                         principal: UserPrincipal,
                                         userId: UserId,
                                         redis: Jedis): String {
    val email = principal.email(redis)
    logger.info { "Deleting user $email" }
    userId.deleteUser(principal, redis)
    call.sessions.clear<UserPrincipal>()
    return requestLogInPage(content, false, "User $email deleted")
  }
}