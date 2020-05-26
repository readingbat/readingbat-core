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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.encode
import com.github.pambrose.common.util.isNotValidEmail
import com.github.pambrose.common.util.randomId
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RESET_ID
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Emailer.sendEmail
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.misc.UserId.Companion.isValidUsername
import com.github.readingbat.misc.UserId.Companion.lookupUserId
import com.github.readingbat.misc.UserId.Companion.passwordResetKey
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.posts.CreateAccount.checkPassword
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.queryParam
import com.google.common.util.concurrent.RateLimiter
import io.ktor.application.call
import io.ktor.request.receiveParameters

internal object PasswordReset {
  private val unknownUserLimiter = RateLimiter.create(0.5) // rate 2.0 is "2 permits per second"

  suspend fun PipelineCall.sendPasswordReset(content: ReadingBatContent) {
    val parameters = call.receiveParameters()
    val username = parameters[USERNAME] ?: ""
    when {
      username.isEmpty() -> {
        respondWith { passwordResetPage(content, "", "Unable to send password reset email -- missing email address") }
      }
      username.isNotValidEmail() -> {
        respondWith { passwordResetPage(content, "", "Invalid email address: $username") }
      }
      !isValidUsername(username) -> {
        unknownUserLimiter.acquire()
        respondWith { passwordResetPage(content, "", "Unknown user: $username") }
      }
      else -> {

        try {
          val resetId = randomId(15)

          withRedisPool { redis ->
            if (redis == null)
              throw InvalidConfigurationException(DBMS_DOWN)

            val userId = lookupUserId(username) ?: throw InvalidConfigurationException("Unable to find $username")

            val passwordResetKey = passwordResetKey(resetId)
            val userIdPasswordResetKey = userId.userIdPasswordResetKey(username)

            // Lookup previous value if it exists
            val previousResetId = redis.get(userIdPasswordResetKey) ?: ""

            redis.multi().also { tx ->
              if (previousResetId.isNotEmpty())
                tx.del(userIdPasswordResetKey)
              tx.set(userIdPasswordResetKey, resetId)
              tx.set(passwordResetKey, username)
              tx.exec()
            }
          }

          sendEmail(to = username,
                    from = "reset@readingbat.com",
                    subject = "ReadingBat password reset",
                    msg =
                    """
                      |This is a password reset message for the http://readingbat.com account for '$username'
                      |Go to this URL to set a new password: http://readingbat.com$PASSWORD_RESET?$RESET_ID=$resetId 
                      |If you did not request to reset your password, please ignore this message.
                    """.trimMargin())

          val returnPath = queryParam(RETURN_PATH) ?: "/"
          redirectTo { "$returnPath?$MSG=${"Password reset email sent to $username".encode()}" }
        } catch (e: Exception) {
          e.printStackTrace()
          respondWith { passwordResetPage(content, "", "Unable to send password reset email to $username") }
        }
      }
    }
  }

  suspend fun PipelineCall.changePassword(content: ReadingBatContent) {
    val parameters = call.parameters
    val resetId = parameters[RESET_ID] ?: ""
    val newPassword = parameters[NEW_PASSWORD] ?: ""
    val confirmPassword = parameters[CONFIRM_PASSWORD] ?: ""

    val passwordError = checkPassword(newPassword, confirmPassword)

    if (passwordError.isNotEmpty()) {
      respondWith { passwordResetPage(content, resetId, passwordError) }
    }
    else {
      val passwordResetKey = passwordResetKey(resetId)

      try {
        withSuspendingRedisPool { redis ->
          if (redis == null) {
            throw InvalidConfigurationException(DBMS_DOWN)
          }
          else {
            val username = redis.get(passwordResetKey)
            val userId = lookupUserId(username) ?: throw InvalidConfigurationException("Unable to find $username")
            val userIdPasswordResetKey = userId.userIdPasswordResetKey(username)
            val salt = redis.get(userId.saltKey())

            redis.multi().also { tx ->
              //tx.del(userIdPasswordResetKey)
              //tx.del(passwordResetKey)
              // Set new password
              //tx.set(userId.passwordKey(), newPassword.sha256(salt))
              //tx.exec()
            }
            redirectTo { "/?$MSG=${"Password reset for $username".encode()}" }
          }
        }

      } catch (e: InvalidConfigurationException) {
        e.printStackTrace()
        respondWith { passwordResetPage(content, resetId, "Unable to reset password") }
      }
    }
  }
}