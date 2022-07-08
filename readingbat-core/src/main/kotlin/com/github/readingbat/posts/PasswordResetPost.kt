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
import com.github.readingbat.common.Constants.INVALID_RESET_ID
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Emailer.sendEmail
import com.github.readingbat.common.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.EMAIL_PARAM
import com.github.readingbat.common.FormFields.NEW_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.RESET_ID_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Property
import com.github.readingbat.common.User.Companion.isNotRegisteredEmail
import com.github.readingbat.common.User.Companion.queryUserByEmail
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.common.isValidUser
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.posts.CreateAccountPost.checkPassword
import com.github.readingbat.server.Email
import com.github.readingbat.server.Email.Companion.getEmail
import com.github.readingbat.server.Password.Companion.getPassword
import com.github.readingbat.server.PasswordResetsTable
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ResetId
import com.github.readingbat.server.ResetId.Companion.EMPTY_RESET_ID
import com.github.readingbat.server.ResetId.Companion.getResetId
import com.github.readingbat.server.ResetId.Companion.newResetId
import com.github.readingbat.server.ServerUtils.queryParam
import com.google.common.util.concurrent.RateLimiter
import com.pambrose.common.exposed.get
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import mu.KLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.IOException

internal object PasswordResetPost : KLogging() {
  private val unknownUserLimiter = RateLimiter.create(0.5) // rate 2.0 is "2 permits per second"
  private val unableToSend = Message("Unable to send password reset email -- missing email address", true)

  suspend fun PipelineCall.sendPasswordReset(): String {
    val email = call.receiveParameters().getEmail(EMAIL_PARAM)
    val remoteStr = call.request.origin.remoteHost
    logger.info { "Password change request for $email - $remoteStr" }
    val user = queryUserByEmail(email)
    return when {
      user.isNotValidUser() -> {
        unknownUserLimiter.acquire()
        passwordResetPage(EMPTY_RESET_ID, Message("Invalid user: $email", true))
      }
      email.isBlank() -> passwordResetPage(EMPTY_RESET_ID, unableToSend)
      email.isNotValidEmail() -> {
        passwordResetPage(EMPTY_RESET_ID, Message("Invalid email address: $email", true))
      }
      isNotRegisteredEmail(email) -> {
        unknownUserLimiter.acquire()
        passwordResetPage(EMPTY_RESET_ID, Message("Unknown user: $email", true))
      }
      else -> {
        try {
          val newResetId = newResetId()

          // Lookup and remove previous value if it exists
          val user2 = queryUserByEmail(email) ?: throw ResetPasswordException("Unable to find $email")
          //val previousResetId = user2.userPasswordResetId()
          user2.savePasswordResetId(email, newResetId)

          logger.info { "Sending password reset email to $email - $remoteStr" }
          try {
            val msg = Message(
              """
              |This is a password reset message for the ReadingBat.com account for '$email'
              |Go to this URL to set a new password: ${Property.SENDGRID_PREFIX.getProperty("")}$PASSWORD_RESET_ENDPOINT?$RESET_ID_PARAM=$newResetId 
              |If you did not request to reset your password, please ignore this message.
            """.trimMargin()
            )
            sendEmail(
              to = email,
              from = Email("reset@readingbat.com"),
              subject = "ReadingBat password reset",
              msg = msg
            )
          } catch (e: IOException) {
            logger.info(e) { e.message }
            throw ResetPasswordException("Unable to send email")
          }

          val returnPath = queryParam(RETURN_PARAM, "/")
          throw RedirectException("$returnPath?$MSG=${"Password reset email sent to $email".encode()}")
        } catch (e: ResetPasswordException) {
          logger.info { e }
          val msg = Message("Unable to send password reset email to $email", true)
          passwordResetPage(EMPTY_RESET_ID, msg)
        }
      }
    }
  }

  suspend fun PipelineCall.updatePassword(): String =
    try {
      val params = call.receiveParameters()
      val resetId = params.getResetId(RESET_ID_PARAM)
      val newPassword = params.getPassword(NEW_PASSWORD_PARAM)
      val confirmPassword = params.getPassword(CONFIRM_PASSWORD_PARAM)
      val passwordError = checkPassword(newPassword, confirmPassword)

      if (!isDbmsEnabled())
        throw ResetPasswordException("Function unavailable without database enabled")

      if (passwordError.isNotBlank)
        throw ResetPasswordException(passwordError.value, resetId)

      val email =
        transaction {
          PasswordResetsTable
            .slice(PasswordResetsTable.email)
            .select { PasswordResetsTable.resetId eq resetId.value }
            .map { it[0] as String }
            .firstOrNull() ?: throw ResetPasswordException(INVALID_RESET_ID)
        }

      val user = queryUserByEmail(Email(email)) ?: throw ResetPasswordException("Unable to find $email")
      val salt = user.salt
      val newDigest = newPassword.sha256(salt)
      val oldDigest = user.digest

      if (!user.isValidUser())
        throw ResetPasswordException("Invalid user", resetId)

      if (newDigest == oldDigest)
        throw ResetPasswordException("New password is the same as the current password", resetId)

      user.assignDigest(newDigest)  // Set new password

      throw RedirectException("/?$MSG=${"Password reset for $email".encode()}")
    } catch (e: ResetPasswordException) {
      logger.info { e }
      passwordResetPage(e.resetId, Message(e.msg))
    }

  class ResetPasswordException(val msg: String, val resetId: ResetId = EMPTY_RESET_ID) : Exception(msg)
}