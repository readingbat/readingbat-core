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
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.EMAIL
import com.github.readingbat.misc.FormFields.FULLNAME
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.User.Companion.createUser
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.CreateAccountPage.createAccountPage
import com.github.readingbat.server.*
import com.github.readingbat.server.Email.Companion.getEmail
import com.github.readingbat.server.FullName.Companion.getFullName
import com.github.readingbat.server.Password.Companion.getPassword
import com.github.readingbat.server.ServerUtils.queryParam
import com.google.common.util.concurrent.RateLimiter
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import mu.KLogging
import redis.clients.jedis.Jedis

internal object CreateAccountPost : KLogging() {

  private const val EMPTY_NAME_MSG = "Empty name value"
  private const val EMPTY_EMAIL_MSG = "Empty email value"
  private const val INVALID_EMAIL_MSG = "Invalid email value"
  private const val EMPTY_PASSWORD_MSG = "Empty password value"
  private const val PASSWORD_TOO_SHORT_MSG = "Password value too short (must have at least 6 characters)"
  private const val NO_MATCH_MSG = "Passwords do not match"
  private const val CLEVER_PASSWORD_MSG = "Surely you can come up with a more clever password"

  private val createAccountLimiter = RateLimiter.create(2.0) // rate 2.0 is "2 permits per second"

  fun checkPassword(newPassword: Password, confirmPassword: Password) =
    when {
      newPassword.isBlank() -> EMPTY_PASSWORD_MSG
      newPassword.length < 6 -> PASSWORD_TOO_SHORT_MSG
      newPassword != confirmPassword -> NO_MATCH_MSG
      newPassword.value == "password" -> CLEVER_PASSWORD_MSG
      else -> ""
    }

  suspend fun PipelineCall.createAccount(content: ReadingBatContent, redis: Jedis): String {
    val parameters = call.receiveParameters()
    val fullName = parameters.getFullName(FULLNAME)
    val email = parameters.getEmail(EMAIL)
    val password = parameters.getPassword(PASSWORD)
    val confirmPassword = parameters.getPassword(CONFIRM_PASSWORD)

    return when {
      fullName.isBlank() -> createAccountPage(content, defaultEmail = email, msg = EMPTY_NAME_MSG)
      email.isBlank() -> createAccountPage(content, defaultFullName = fullName, msg = EMPTY_EMAIL_MSG)
      email.isNotValidEmail() ->
        createAccountPage(content,
                          defaultFullName = fullName,
                          defaultEmail = email,
                          msg = INVALID_EMAIL_MSG)
      else -> {
        val passwordError = checkPassword(password, confirmPassword)
        if (passwordError.isNotEmpty())
          createAccountPage(content,
                            defaultFullName = fullName,
                            defaultEmail = email,
                            msg = passwordError)
        else
          createAccount(content, fullName, email, password, redis)
      }
    }
  }

  private fun PipelineCall.createAccount(content: ReadingBatContent,
                                         name: FullName,
                                         email: Email,
                                         password: Password,
                                         redis: Jedis): String {
    val returnPath = queryParam(RETURN_PATH) ?: "/"

    createAccountLimiter.acquire() // may wait

    // Check if email already exists
    return if (redis.exists(email.userEmailKey)) {
      createAccountPage(content, msg = "Email already registered: $email")
    }
    else {
      // Create user
      val user = createUser(name, email, password, redis)
      call.sessions.set(UserPrincipal(userId = user.id))
      throw RedirectException("$returnPath?$MSG=${"User $email created".encode()}")
    }
  }
}