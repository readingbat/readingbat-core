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

import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.DataException
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.CURR_PASSWORD
import com.github.readingbat.misc.FormFields.DELETE_ACCOUNT
import com.github.readingbat.misc.FormFields.JOIN_CLASS
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.FormFields.WITHDRAW_FROM_CLASS
import com.github.readingbat.misc.KeyConstants.DIGEST_FIELD
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.lookupDigestInfoByUserId
import com.github.readingbat.misc.UserId.Companion.userIdByPrincipal
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.UserPrefsPage.fetchClassDesc
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

internal object UserPrefs : KLogging() {

  suspend fun PipelineCall.userPrefs(content: ReadingBatContent, redis: Jedis): String {
    val parameters = call.receiveParameters()
    val principal = fetchPrincipal()
    val userId = userIdByPrincipal(principal)

    return if (userId == null || principal == null) {
      requestLogInPage(content, redis)
    }
    else {
      when (val action = parameters[USER_PREFS_ACTION] ?: "") {
        UPDATE_PASSWORD -> updatePassword(content, redis, parameters, userId)
        JOIN_CLASS -> enrollInClass(content, parameters, userId, redis)
        WITHDRAW_FROM_CLASS -> withdrawFromClass(content, redis, userId)
        DELETE_ACCOUNT -> deleteAccount(content, principal, userId, redis)
        else -> throw InvalidConfigurationException("Invalid action: $action")
      }
    }
  }

  private fun PipelineCall.updatePassword(content: ReadingBatContent,
                                          redis: Jedis,
                                          parameters: Parameters,
                                          userId: UserId): String {
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

    return userPrefsPage(content, redis, msg.first, msg.second)
  }

  private fun PipelineCall.enrollInClass(content: ReadingBatContent,
                                         parameters: Parameters,
                                         userId: UserId,
                                         redis: Jedis): String {
    val classCode = parameters[CLASS_CODE] ?: ""
    return try {
      userId.enrollInClass(classCode, redis)
      val classDesc = fetchClassDesc(classCode, redis)
      userPrefsPage(content, redis, "Enrolled in class $classCode [$classDesc]", false)
    } catch (e: DataException) {
      userPrefsPage(content,
                    redis,
                    "Unable to join class [${e.msg}]",
                    true,
                    defaultClassCode = classCode)
    }
  }

  private fun PipelineCall.withdrawFromClass(content: ReadingBatContent, redis: Jedis, userId: UserId): String {
    val enrolledClassCode = userId.fetchEnrolledClassCode(redis)
    val classDesc = fetchClassDesc(enrolledClassCode, redis)

    return try {
      userId.withdrawFromClass(enrolledClassCode, redis)
      userPrefsPage(content, redis, "Withdrawn from class $enrolledClassCode [$classDesc]", false)
    } catch (e: DataException) {
      userPrefsPage(content, redis, "Unable to withdraw from class [${e.msg}]", true)
    }
  }

  private fun PipelineCall.deleteAccount(content: ReadingBatContent,
                                         principal: UserPrincipal,
                                         userId: UserId,
                                         redis: Jedis): String {
    val email = principal.email(redis)
    logger.info { "Deleting user $email" }
    userId.deleteUser(principal, redis)
    call.sessions.clear<UserPrincipal>()
    return requestLogInPage(content, redis, false, "User $email deleted")
  }
}