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
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.Constants.INVALID_RESET_ID
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Constants.RESET_ID
import com.github.readingbat.common.Constants.RETURN_PATH
import com.github.readingbat.common.Emailer.sendEmail
import com.github.readingbat.common.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.common.FormFields.EMAIL
import com.github.readingbat.common.FormFields.NEW_PASSWORD
import com.github.readingbat.common.Message
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.isRegisteredEmail
import com.github.readingbat.common.User.Companion.lookupUserByEmail
import com.github.readingbat.common.isValidUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.posts.CreateAccountPost.checkPassword
import com.github.readingbat.server.Email
import com.github.readingbat.server.Email.Companion.getEmail
import com.github.readingbat.server.Password.Companion.getPassword
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ResetId
import com.github.readingbat.server.ResetId.Companion.EMPTY_RESET_ID
import com.github.readingbat.server.ResetId.Companion.getResetId
import com.github.readingbat.server.ResetId.Companion.newResetId
import com.github.readingbat.server.ServerUtils.queryParam
import com.google.common.util.concurrent.RateLimiter
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.sessions.*
import mu.KLogging
import redis.clients.jedis.Jedis
import java.io.IOException

internal object PasswordResetPost : KLogging() {
  private val unknownUserLimiter = RateLimiter.create(0.5) // rate 2.0 is "2 permits per second"

  suspend fun PipelineCall.sendPasswordReset(content: ReadingBatContent, user: User?, redis: Jedis): String {
    val parameters = call.receiveParameters()
    val email = parameters.getEmail(EMAIL)
    return when {
      !user.isValidUser(redis) -> {
        unknownUserLimiter.acquire()
        passwordResetPage(content, EMPTY_RESET_ID, redis, Message("Invalid User", true))
      }
      email.isBlank() -> {
        passwordResetPage(content,
                          EMPTY_RESET_ID,
                          redis,
                          Message("Unable to send password reset email -- missing email address", true))
      }
      email.isNotValidEmail() -> {
        passwordResetPage(content, EMPTY_RESET_ID, redis, Message("Invalid email address: $email", true))
      }
      !isRegisteredEmail(email, redis) -> {
        unknownUserLimiter.acquire()
        passwordResetPage(content, EMPTY_RESET_ID, redis, Message("Unknown user: $email", true))
      }
      else -> {
        try {
          val newResetId = newResetId()
          val browserSession = call.sessions.get<BrowserSession>()

          // Lookup and remove previous value if it exists
          val user2 = lookupUserByEmail(email, redis) ?: throw ResetPasswordException("Unable to find $email")
          val previousResetId = user2.passwordResetKey(redis)?.let { ResetId(it) } ?: EMPTY_RESET_ID

          redis.multi().also { tx ->
            user2.savePasswordResetKey(email, previousResetId, newResetId, tx)
            tx.exec()
          }

          logger.info { "Sending password reset email to $email" }
          try {
            val msg = Message("""
              |This is a password reset message for the ReadingBat.com account for '$email'
              |Go to this URL to set a new password: ${content.urlPrefix}$PASSWORD_RESET_ENDPOINT?$RESET_ID=$newResetId 
              |If you did not request to reset your password, please ignore this message.
            """.trimMargin())
            sendEmail(to = email,
                      from = Email("reset@readingbat.com"),
                      subject = "ReadingBat password reset",
                      msg = msg)
          } catch (e: IOException) {
            logger.info(e) { e.message }
            throw ResetPasswordException("Unable to send email")
          }

          val returnPath = queryParam(RETURN_PATH, "/")
          throw RedirectException("$returnPath?$MSG=${"Password reset email sent to $email".encode()}")
        } catch (e: ResetPasswordException) {
          logger.info { e }
          passwordResetPage(content,
                            EMPTY_RESET_ID,
                            redis,
                            Message("Unable to send password reset email to $email", true))
        }
      }
    }
  }

  suspend fun PipelineCall.changePassword(content: ReadingBatContent, redis: Jedis): String =
    try {
      val parameters = call.receiveParameters()
      val resetId = parameters.getResetId(RESET_ID)
      val newPassword = parameters.getPassword(NEW_PASSWORD)
      val confirmPassword = parameters.getPassword(CONFIRM_PASSWORD)
      val passwordError = checkPassword(newPassword, confirmPassword)

      if (passwordError.isNotBlank)
        throw ResetPasswordException(passwordError.value, resetId)

      val passwordResetKey = resetId.passwordResetKey
      val email = Email(redis.get(passwordResetKey) ?: throw ResetPasswordException(INVALID_RESET_ID))
      val user = lookupUserByEmail(email, redis) ?: throw ResetPasswordException("Unable to find $email")
      val salt = user.salt(redis)
      val newDigest = newPassword.sha256(salt)
      val oldDigest = user.digest(redis)

      if (!user.isValidUser(redis))
        throw ResetPasswordException("Invalid user", resetId)

      if (newDigest == oldDigest)
        throw ResetPasswordException("New password is the same as the current password", resetId)

      redis.multi().also { tx ->
        user.deletePasswordResetKey(tx)
        tx.del(passwordResetKey)
        user.assignDigest(tx, newDigest)  // Set new password
        tx.exec()
      }
      throw RedirectException("/?$MSG=${"Password reset for $email".encode()}")
    } catch (e: ResetPasswordException) {
      logger.info { e }
      passwordResetPage(content, e.resetId, redis, Message(e.msg))
    }

  class ResetPasswordException(val msg: String, val resetId: ResetId = EMPTY_RESET_ID) : Exception(msg)
}