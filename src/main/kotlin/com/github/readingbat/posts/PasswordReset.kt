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
import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.Constants.INVALID_RESET_ID
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RESET_ID
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Emailer.sendEmail
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.misc.UserId.Companion.isValidUsername
import com.github.readingbat.misc.UserId.Companion.lookupUsername
import com.github.readingbat.misc.UserId.Companion.passwordResetKey
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.posts.CreateAccount.checkPassword
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.production
import com.github.readingbat.server.queryParam
import com.google.common.util.concurrent.RateLimiter
import io.ktor.application.call
import io.ktor.request.receiveParameters
import mu.KLogging
import java.io.IOException

internal object PasswordReset : KLogging() {
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
              throw ResetPasswordException(DBMS_DOWN)

            // Lookup and remove previous value if it exists
            val userId = lookupUsername(username, redis) ?: throw ResetPasswordException("Unable to find $username")
            val userIdPasswordResetKey = userId.userIdPasswordResetKey()
            val previousResetId = redis.get(userIdPasswordResetKey) ?: ""

            redis.multi().also { tx ->
              if (previousResetId.isNotEmpty()) {
                tx.del(userIdPasswordResetKey)
                tx.del(passwordResetKey(previousResetId))
              }

              tx.set(userIdPasswordResetKey, resetId)
              tx.set(passwordResetKey(resetId), username)

              tx.exec()
            }
          }

          try {
            val host = if (production) "https://readingbat.com" else "http://0.0.0.0:8080"
            sendEmail(to = username,
                      from = "reset@readingbat.com",
                      subject = "ReadingBat password reset",
                      msg =
                      """
                      |This is a password reset message for the http://readingbat.com account for '$username'
                      |Go to this URL to set a new password: $host$PASSWORD_RESET?$RESET_ID=$resetId 
                      |If you did not request to reset your password, please ignore this message.
                    """.trimMargin())
          } catch (e: IOException) {
            logger.info(e) { e.message }
            throw ResetPasswordException("Unable to send email")
          }

          val returnPath = queryParam(RETURN_PATH) ?: "/"
          redirectTo { "$returnPath?$MSG=${"Password reset email sent to $username".encode()}" }
        } catch (e: ResetPasswordException) {
          logger.info { e }
          respondWith { passwordResetPage(content, "", "Unable to send password reset email to $username") }
        }
      }
    }
  }

  suspend fun PipelineCall.changePassword(content: ReadingBatContent) =
    try {
      val parameters = call.receiveParameters()
      val resetId = parameters[RESET_ID] ?: ""
      val newPassword = parameters[NEW_PASSWORD] ?: ""
      val confirmPassword = parameters[CONFIRM_PASSWORD] ?: ""
      val passwordError = checkPassword(newPassword, confirmPassword)

      if (passwordError.isNotEmpty())
        throw ResetPasswordException(passwordError, resetId)

      withSuspendingRedisPool { redis ->
        if (redis == null)
          throw ResetPasswordException(DBMS_DOWN, resetId)

        val passwordResetKey = passwordResetKey(resetId)
        val username = redis.get(passwordResetKey) ?: throw ResetPasswordException(INVALID_RESET_ID)
        val userId = lookupUsername(username, redis) ?: throw ResetPasswordException("Unable to find $username")
        val userIdPasswordResetKey = userId.userIdPasswordResetKey()
        val passwordKey = userId.passwordKey()
        val salt = redis.get(userId.saltKey())
        val newDigest = newPassword.sha256(salt)
        val oldDigest = redis.get(passwordKey)

        if (newDigest == oldDigest)
          throw ResetPasswordException("New password is the same as the current password", resetId)

        redis.multi().also { tx ->
          tx.del(userIdPasswordResetKey)
          tx.del(passwordResetKey)
          tx.set(passwordKey, newDigest)  // Set new password
          tx.exec()
        }
        redirectTo { "/?$MSG=${"Password reset for $username".encode()}" }
      }
    } catch (e: ResetPasswordException) {
      logger.info { e }
      respondWith { passwordResetPage(content, e.resetId, e.msg) }
    }

  class ResetPasswordException(val msg: String, val resetId: String = "") : Exception(msg)
}