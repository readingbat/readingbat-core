/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.posts

import com.readingbat.common.ClassCode.Companion.getClassCode
import com.readingbat.common.ClassCodeRepository.toDisplayString
import com.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.readingbat.common.FormFields.DEFAULT_LANGUAGE_CHOICE_PARAM
import com.readingbat.common.FormFields.DELETE_ACCOUNT
import com.readingbat.common.FormFields.JOIN_CLASS
import com.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.readingbat.common.FormFields.UPDATE_DEFAULT_LANGUAGE
import com.readingbat.common.FormFields.WITHDRAW_FROM_CLASS
import com.readingbat.common.Message
import com.readingbat.common.User
import com.readingbat.common.UserPrincipal
import com.readingbat.common.isValidUser
import com.readingbat.common.nowInstant
import com.readingbat.dsl.DataException
import com.readingbat.dsl.LanguageType.Companion.getLanguageType
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.pages.UserPrefsPage.requestLogInPage
import com.readingbat.pages.UserPrefsPage.userPrefsPage
import com.readingbat.server.UsersTable
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Parameters
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Handles POST submissions from the user preferences page.
 *
 * Dispatches actions including updating the default language, enrolling in or
 * withdrawing from a class, and permanently deleting the user account.
 */
internal object UserPrefsPost {
  private val logger = KotlinLogging.logger {}

  suspend fun RoutingContext.userPrefs(content: ReadingBatContent, user: User?) =
    if (user.isValidUser()) {
      val params = call.receiveParameters()
      when (val action = params[PREFS_ACTION_PARAM] ?: "") {
        UPDATE_DEFAULT_LANGUAGE -> updateDefaultLanguage(content, params, user)
        JOIN_CLASS -> enrollInClass(content, params, user)
        WITHDRAW_FROM_CLASS -> withdrawFromClass(content, user)
        DELETE_ACCOUNT -> deleteAccount(content, user)
        else -> error("Invalid action: $action")
      }
    } else {
      requestLogInPage(content)
    }

  private fun RoutingContext.updateDefaultLanguage(
    content: ReadingBatContent,
    parameters: Parameters,
    user: User,
  ) =
    parameters.getLanguageType(DEFAULT_LANGUAGE_CHOICE_PARAM)
      .let {
        transaction {
          with(UsersTable) {
            update({ id eq user.userDbmsId }) { row ->
              row[updated] = nowInstant()
              row[defaultLanguage] = it.languageName.value
              user.defaultLanguage = it
            }
          }
        }
        userPrefsPage(content, user, Message("Default language updated to $it", false))
      }

  private fun RoutingContext.enrollInClass(
    content: ReadingBatContent,
    parameters: Parameters,
    user: User,
  ): String {
    val classCode = parameters.getClassCode(CLASS_CODE_NAME_PARAM)
    return try {
      user.enrollInClass(classCode)
      userPrefsPage(content, user, Message("Enrolled in class ${classCode.toDisplayString()}"))
    } catch (e: DataException) {
      userPrefsPage(content, user, Message("Unable to join class [${e.msg}]", true), classCode)
    }
  }

  private fun RoutingContext.withdrawFromClass(content: ReadingBatContent, user: User) =
    try {
      val enrolledClassCode = user.enrolledClassCode
      user.withdrawFromClass(enrolledClassCode)
      userPrefsPage(content, user, Message("Withdrawn from class ${enrolledClassCode.toDisplayString()}"))
    } catch (e: DataException) {
      userPrefsPage(content, user, Message("Unable to withdraw from class [${e.msg}]", true))
    }

  private fun RoutingContext.deleteAccount(content: ReadingBatContent, user: User): String {
    val email = user.email
    logger.info { "Deleting user $email" }
    user.deleteUser()
    call.sessions.clear<UserPrincipal>()
    return requestLogInPage(content, Message("User $email deleted"))
  }
}
