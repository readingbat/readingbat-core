/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.EMAIL_PARAM
import com.github.readingbat.common.FormFields.FULLNAME_PARAM
import com.github.readingbat.common.FormFields.PASSWORD_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.User.Companion.createUser
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.common.browserSession
import com.github.readingbat.pages.CreateAccountPage.createAccountPage
import com.github.readingbat.server.Email
import com.github.readingbat.server.Email.Companion.getEmail
import com.github.readingbat.server.FullName
import com.github.readingbat.server.FullName.Companion.getFullName
import com.github.readingbat.server.Password
import com.github.readingbat.server.Password.Companion.getPassword
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.UsersTable
import com.github.readingbat.utils.ExposedUtils.readonlyTx
import com.google.common.util.concurrent.RateLimiter
import com.pambrose.common.exposed.get
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import mu.two.KLogging
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.select

internal object CreateAccountPost : KLogging() {

  private val EMPTY_NAME_MSG = Message("Empty name value", true)
  private val EMPTY_EMAIL_MSG = Message("Empty email value", true)
  private val INVALID_EMAIL_MSG = Message("Invalid email value", true)
  private val EMPTY_PASSWORD_MSG = Message("Empty password value", true)
  private val PASSWORD_TOO_SHORT_MSG = Message("Password value too short (must have at least 6 characters)", true)
  private val NO_MATCH_MSG = Message("Passwords do not match", true)
  private val CLEVER_PASSWORD_MSG = Message("Surely you can come up with a more clever password", true)

  private val createAccountLimiter = RateLimiter.create(2.0) // rate 2.0 is "2 permits per second"

  fun checkPassword(newPassword: Password, confirmPassword: Password) =
    when {
      newPassword.isBlank() -> EMPTY_PASSWORD_MSG
      newPassword.length < 6 -> PASSWORD_TOO_SHORT_MSG
      newPassword != confirmPassword -> NO_MATCH_MSG
      newPassword.value == "password" -> CLEVER_PASSWORD_MSG
      else -> EMPTY_MESSAGE
    }

  suspend fun PipelineCall.createAccount(): String {
    val params = call.receiveParameters()
    val fullName = params.getFullName(FULLNAME_PARAM)
    val email = params.getEmail(EMAIL_PARAM)
    val password = params.getPassword(PASSWORD_PARAM)
    val confirmPassword = params.getPassword(CONFIRM_PASSWORD_PARAM)

    return when {
      fullName.isBlank() -> createAccountPage(defaultEmail = email, msg = EMPTY_NAME_MSG)
      email.isBlank() -> createAccountPage(defaultFullName = fullName, msg = EMPTY_EMAIL_MSG)
      email.isNotValidEmail() ->
        createAccountPage(defaultFullName = fullName, defaultEmail = email, msg = INVALID_EMAIL_MSG)

      else -> {
        val passwordError = checkPassword(password, confirmPassword)
        if (passwordError.isNotBlank)
          createAccountPage(defaultFullName = fullName, defaultEmail = email, msg = passwordError)
        else
          createAccount(fullName, email, password)
      }
    }
  }

  private fun emailExists(email: Email) =
    readonlyTx {
      UsersTable
        .slice(Count(UsersTable.id))
        .select { UsersTable.email eq email.value }
        .map { it[0] as Long }
        .first() > 0
    }

  private fun PipelineCall.createAccount(name: FullName, email: Email, password: Password): String {
    createAccountLimiter.acquire() // may wait

    // Check if email already exists
    return if (emailExists(email)) {
      createAccountPage(msg = Message("Email already registered: $email"))
    } else {
      // Create user
      val browserSession = call.browserSession
      val user = createUser(name, email, password, browserSession)
      call.sessions.set(UserPrincipal(userId = user.userId))
      val returnPath = queryParam(RETURN_PARAM, "/")
      throw RedirectException("$returnPath?$MSG=${"User $email created".encode()}")
    }
  }
}