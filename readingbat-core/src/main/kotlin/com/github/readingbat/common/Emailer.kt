/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

internal object Emailer {
//  private val logger = KotlinLogging.logger {}

//  fun sendEmail(to: Email, from: Email, subject: String, msg: Message) {
//    val content = Content("text/plain", msg.value)
//    val mail =
//      Mail(
//        com.sendgrid.helpers.mail.objects.Email(from.value),
//        subject,
//        com.sendgrid.helpers.mail.objects.Email(to.value),
//        content,
//      )
//    val sendGrid = SendGrid(EnvVar.SENDGRID_API_KEY.getRequiredEnv())
//
//    val request =
//      Request()
//        .apply {
//          method = Method.POST
//          endpoint = "mail/send"
//          body = mail.build()
//        }
//
//    sendGrid.api(request)
//      .apply {
//        logger.info { "SendGrid response status code: $statusCode to: $to from: $from" }
//        if (body.isNotBlank())
//          logger.info { "Response body: $body" }
//        logger.info { "Response headers: $headers" }
//      }
//  }
}
