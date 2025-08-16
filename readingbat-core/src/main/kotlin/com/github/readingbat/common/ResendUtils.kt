/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.common

import com.github.readingbat.server.Email
import com.github.readingbat.server.Email.Companion.toEmail
import com.resend.Resend
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import io.github.oshai.kotlinlogging.KotlinLogging

object ResendUtils {
  private val logger = KotlinLogging.logger {}
  val envResendApiKey: String by lazy { Property.RESEND_API_KEY.getRequiredProperty() }
  val envResendSender: Email by lazy { Property.RESEND_SENDER_EMAIL.getRequiredProperty().toEmail() }

  fun sendEmail(
    from: Email,
    to: List<Email>,
    cc: List<Email> = emptyList(),
    bcc: List<Email> = emptyList(),
    subject: String,
    html: String,
  ) {
    runCatching {
      val resend = Resend(envResendApiKey)
      val request =
        CreateEmailOptions.builder().run {
          from(from.value)
          to(to.map { it.value })
          cc(cc.map { it.value })
          bcc(bcc.map { it.value })
          subject(subject)
          html(html)
          build()
        }
      val response: CreateEmailResponse = resend.emails().send(request)

      val toStr = to.joinToString(", ").let { it.ifBlank { "None" } }
      val ccStr = cc.joinToString(", ").let { it.ifBlank { "None" } }
      val bccStr = bcc.joinToString(", ").let { it.ifBlank { "None" } }

      logger.info { "Sent email to: $toStr cc: $ccStr bcc: $bccStr [${response.id}]" }
    }.onFailure { e ->
      logger.error(e) { "sendEmail() error: ${e.message}" }
    }
  }
}
