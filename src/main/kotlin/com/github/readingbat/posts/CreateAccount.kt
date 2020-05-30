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
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.encode
import com.github.pambrose.common.util.isNotValidEmail
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.EMAIL
import com.github.readingbat.misc.FormFields.NAME
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.UserId.Companion.createUser
import com.github.readingbat.misc.UserId.Companion.userEmailKey
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.CreateAccountPage.createAccountPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import com.google.common.util.concurrent.RateLimiter
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import mu.KLogging

internal object CreateAccount : KLogging() {

  private const val EMPTY_NAME = "Empty name value"
  private const val EMPTY_EMAIL = "Empty email value"
  private const val INVALID_EMAIL = "Invalid email value"
  private const val EMPTY_PASWORD = "Empty password value"
  private const val PASSWORD_TOO_SHORT = "Password value too short (must have at least 6 characters)"
  private const val NO_MATCH = "Passwords do not match"
  private const val CLEVER_PASSWORD = "Surely you can come up with a more clever password"

  private val createAccountLimiter = RateLimiter.create(2.0) // rate 2.0 is "2 permits per second"

  fun checkPassword(newPassword: String, confirmPassword: String) =
    when {
      newPassword.isBlank() -> EMPTY_PASWORD
      newPassword.length < 6 -> PASSWORD_TOO_SHORT
      newPassword != confirmPassword -> NO_MATCH
      newPassword == "password" -> CLEVER_PASSWORD
      else -> ""
    }

  suspend fun PipelineCall.createAccount(content: ReadingBatContent) {
    val parameters = call.receiveParameters()
    val name = parameters[NAME] ?: ""
    val email = parameters[EMAIL] ?: ""
    val password = parameters[PASSWORD] ?: ""
    val confirmPassword = parameters[CONFIRM_PASSWORD] ?: ""

    when {
      name.isBlank() -> respondWith { createAccountPage(content, defaultEmail = email, msg = EMPTY_NAME) }
      email.isBlank() -> respondWith { createAccountPage(content, defaultName = name, msg = EMPTY_EMAIL) }
      email.isNotValidEmail() -> respondWith {
        createAccountPage(content,
                          defaultName = name,
                          defaultEmail = email,
                          msg = INVALID_EMAIL)
      }
      else -> {
        val passwordError = checkPassword(password, confirmPassword)
        if (passwordError.isNotEmpty())
          respondWith {
            createAccountPage(content,
                              defaultName = name,
                              defaultEmail = email,
                              msg = passwordError)
          }
        else
          createAccount(content, name, email, password)
      }
    }
  }

  private suspend fun PipelineCall.createAccount(content: ReadingBatContent,
                                                 name: String,
                                                 email: String,
                                                 password: String) {
    withSuspendingRedisPool { redis ->
      val returnPath = queryParam(RETURN_PATH) ?: "/"
      if (redis == null) {
        redirectTo { returnPath }
      }
      else {
        createAccountLimiter.acquire() // may wait

        // Check if email already exists
        if (redis.exists(userEmailKey(email))) {
          respondWith { createAccountPage(content, msg = "Email already registered: $email") }
        }
        else {
          // Create user
          val userId = createUser(name, email, password, redis)
          // Assign principal cookie
          call.sessions.set(UserPrincipal(userId = userId.id))
          redirectTo { "$returnPath?$MSG=${"User $email created".encode()}" }
        }
      }
    }
  }
}
