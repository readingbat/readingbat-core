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

import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.encode
import com.github.pambrose.common.util.isNotValidEmail
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Emailer.sendEmail
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.misc.UserId.Companion.isValidUserId
import com.github.readingbat.pages.ResetPasswordPage.resetPasswordPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.queryParam
import io.ktor.application.call
import io.ktor.request.receiveParameters

internal object ResetPassword {

  suspend fun PipelineCall.sendResetPassword(content: ReadingBatContent) {
    val parameters = call.receiveParameters()
    val userId = parameters[USERNAME] ?: ""
    when {
      userId.isEmpty() -> {
        respondWith { resetPasswordPage(content, "Unable to send password reset email -- missing email address") }
      }
      userId.isNotValidEmail() -> {
        respondWith { resetPasswordPage(content, "Invalid email address: $userId") }
      }
      isValidUserId(userId) -> {
        respondWith { resetPasswordPage(content, "Unknown user: $userId") }
      }
      else -> {
        try {
          sendEmail(to = userId,
                    from = "reset@readingbat.com",
                    subject = "ReadingBat password reset",
                    msg =
                    """
                |This is a password reset message for the http://readingbat.com account for '$userId'
                |Log in to http://readingbat.com with the following temporary password: 1203600741
                |Then use the preferences page to set a new password.
                |If you did not request this password, please ignore this message.
               """.trimMargin())

          val returnPath = queryParam(RETURN_PATH) ?: "/"
          redirectTo { "$returnPath?$MSG=${"Password reset email sent to $userId".encode()}" }
        } catch (e: Exception) {
          e.printStackTrace()
          respondWith { resetPasswordPage(content, "Unable to send password reset email to $userId") }
        }
      }
    }
  }
}