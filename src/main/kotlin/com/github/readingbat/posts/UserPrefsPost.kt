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
import com.github.readingbat.misc.*
import com.github.readingbat.misc.ClassCode.Companion.getClassCode
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
import com.github.readingbat.misc.User.Companion.fetchEnrolledClassCode
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.CreateAccountPost.checkPassword
import com.github.readingbat.server.Password.Companion.getPassword
import com.github.readingbat.server.PipelineCall
import io.ktor.application.call
import io.ktor.http.Parameters
import io.ktor.request.receiveParameters
import io.ktor.sessions.clear
import io.ktor.sessions.sessions
import mu.KLogging
import redis.clients.jedis.Jedis

internal object UserPrefsPost : KLogging() {

  suspend fun PipelineCall.userPrefs(content: ReadingBatContent, user: User?, redis: Jedis): String {
    val parameters = call.receiveParameters()

    return if (user.isValidUser(redis)) {
      when (val action = parameters[USER_PREFS_ACTION] ?: "") {
        UPDATE_PASSWORD -> updatePassword(content, parameters, user, redis)
        JOIN_CLASS -> enrollInClass(content, parameters, user, redis)
        WITHDRAW_FROM_CLASS -> withdrawFromClass(content, user, redis)
        DELETE_ACCOUNT -> deleteAccount(content, user, redis)
        else -> throw InvalidConfigurationException("Invalid action: $action")
      }
    }
    else {
      requestLogInPage(content, redis)
    }
  }

  private fun PipelineCall.updatePassword(content: ReadingBatContent,
                                          parameters: Parameters,
                                          user: User,
                                          redis: Jedis): String {
    val currPassword = parameters.getPassword(CURR_PASSWORD)
    val newPassword = parameters.getPassword(NEW_PASSWORD)
    val confirmPassword = parameters.getPassword(CONFIRM_PASSWORD)
    val passwordError = checkPassword(newPassword, confirmPassword)
    val msg =
      if (passwordError.isNotBlank) {
        passwordError
      }
      else {
        val (salt, digest) = user.lookupDigestInfoByUser(redis)
        if (salt.isNotEmpty() && digest.isNotEmpty() && digest == currPassword.sha256(salt)) {
          val newDigest = newPassword.sha256(salt)
          redis.hset(user.userInfoKey, DIGEST_FIELD, newDigest)
          Message("Password changed")
        }
        else {
          Message("Incorrect current password", true)
        }
      }

    return userPrefsPage(content, user, redis, msg)
  }

  private fun PipelineCall.enrollInClass(content: ReadingBatContent,
                                         parameters: Parameters,
                                         user: User,
                                         redis: Jedis): String {
    val classCode = parameters.getClassCode(CLASS_CODE)
    return try {
      user.enrollInClass(classCode, redis)
      val classDesc = classCode.fetchClassDesc(redis)
      userPrefsPage(content, user, redis, Message("Enrolled in class $classDesc [$classCode]"))
    } catch (e: DataException) {
      userPrefsPage(content, user, redis, Message("Unable to join class [${e.msg}]", true), classCode)
    }
  }

  private fun PipelineCall.withdrawFromClass(content: ReadingBatContent, user: User, redis: Jedis) =
    try {
      val enrolledClassCode = user.fetchEnrolledClassCode(redis)
      val classDesc = enrolledClassCode.fetchClassDesc(redis)
      user.withdrawFromClass(enrolledClassCode, redis)
      userPrefsPage(content, user, redis, Message("Withdrawn from class $classDesc [$enrolledClassCode]"))
    } catch (e: DataException) {
      userPrefsPage(content, user, redis, Message("Unable to withdraw from class [${e.msg}]", true))
    }

  private fun PipelineCall.deleteAccount(content: ReadingBatContent, user: User, redis: Jedis): String {
    val email = user.email(redis)
    logger.info { "Deleting user $email" }
    user.deleteUser(user, redis)
    call.sessions.clear<UserPrincipal>()
    return requestLogInPage(content, redis, Message("User $email deleted"))
  }
}