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

import com.github.readingbat.common.*
import com.github.readingbat.common.ClassCode.Companion.getClassCode
import com.github.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.CURR_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.DEFAULT_LANGUAGE_CHOICE_PARAM
import com.github.readingbat.common.FormFields.DELETE_ACCOUNT
import com.github.readingbat.common.FormFields.JOIN_CLASS
import com.github.readingbat.common.FormFields.NEW_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.github.readingbat.common.FormFields.UPDATE_DEFAULT_LANGUAGE
import com.github.readingbat.common.FormFields.UPDATE_PASSWORD
import com.github.readingbat.common.FormFields.WITHDRAW_FROM_CLASS
import com.github.readingbat.dsl.DataException
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType.Companion.getLanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.CreateAccountPost.checkPassword
import com.github.readingbat.server.Password.Companion.getPassword
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ReadingBatServer.usePostgres
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.sessions.*
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import redis.clients.jedis.Jedis

internal object UserPrefsPost : KLogging() {

  suspend fun PipelineCall.userPrefs(content: ReadingBatContent, user: User?, redis: Jedis) =
    if (user.isValidUser(redis)) {
      val parameters = call.receiveParameters()
      when (val action = parameters[PREFS_ACTION_PARAM] ?: "") {
        UPDATE_DEFAULT_LANGUAGE -> updateDefaultLanguage(content, parameters, user, redis)
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

  private fun PipelineCall.updateDefaultLanguage(content: ReadingBatContent,
                                                 parameters: Parameters,
                                                 user: User,
                                                 redis: Jedis) =
    parameters.getLanguageType(DEFAULT_LANGUAGE_CHOICE_PARAM)
      .let {
        transaction {
          Users
            .update({ Users.id eq user.userDbmsId }) { row ->
              row[updated] = DateTime.now(UTC)
              row[defaultLanguage] = it.languageName.value
              user.defaultLanguage = it
            }
        }
        userPrefsPage(content, user, redis, Message("Default language updated to $it", true))
      }

  private fun PipelineCall.updatePassword(content: ReadingBatContent,
                                          parameters: Parameters,
                                          user: User,
                                          redis: Jedis): String {
    val currPassword = parameters.getPassword(CURR_PASSWORD_PARAM)
    val newPassword = parameters.getPassword(NEW_PASSWORD_PARAM)
    val confirmPassword = parameters.getPassword(CONFIRM_PASSWORD_PARAM)
    val passwordError = checkPassword(newPassword, confirmPassword)
    val msg =
      if (passwordError.isNotBlank) {
        passwordError
      }
      else {
        val salt = user.salt
        val oldDigest = user.digest
        if (salt.isNotEmpty() && oldDigest.isNotEmpty() && oldDigest == currPassword.sha256(salt)) {
          val newDigest = newPassword.sha256(salt)
          if (newDigest == oldDigest)
            Message("New password is the same as the current password", true)
          else {
            if (usePostgres)
              transaction {
                user.assignDigest(newDigest)
              }
            else
              redis.multi().also { tx -> user.assignDigest(tx, newDigest) }
            Message("Password changed")
          }
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
    val classCode = parameters.getClassCode(CLASS_CODE_NAME_PARAM)
    return try {
      user.enrollInClass(classCode, redis)
      userPrefsPage(content, user, redis, Message("Enrolled in class ${classCode.toDisplayString(redis)}"))
    } catch (e: DataException) {
      userPrefsPage(content, user, redis, Message("Unable to join class [${e.msg}]", true), classCode)
    }
  }

  private fun PipelineCall.withdrawFromClass(content: ReadingBatContent, user: User, redis: Jedis) =
    try {
      val enrolledClassCode = user.enrolledClassCode
      user.withdrawFromClass(enrolledClassCode, redis)
      userPrefsPage(content, user, redis, Message("Withdrawn from class ${enrolledClassCode.toDisplayString(redis)}"))
    } catch (e: DataException) {
      userPrefsPage(content, user, redis, Message("Unable to withdraw from class [${e.msg}]", true))
    }

  private fun PipelineCall.deleteAccount(content: ReadingBatContent, user: User, redis: Jedis): String {
    val email = user.email
    logger.info { "Deleting user $email" }
    user.deleteUser(redis)
    call.sessions.clear<UserPrincipal>()
    return requestLogInPage(content, redis, Message("User $email deleted"))
  }
}