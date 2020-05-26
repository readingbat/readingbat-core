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

package com.github.readingbat.misc

import com.sendgrid.*
import mu.KLogging
import java.io.IOException


internal object Emailer : KLogging() {

  fun sendEmail(to: String, from: String, subject: String, msg: String) {
    val content = Content("text/plain", msg)
    val mail = Mail(Email(from), subject, Email(to), content)
    val sg = SendGrid(System.getenv("SENDGRID_API_KEY"))
    try {
      val request =
        Request().apply {
          setMethod(Method.POST)
          setEndpoint("mail/send")
          setBody(mail.build())
        }

      sg.api(request).also { response ->
        logger.info { "Status code: ${response.getStatusCode()} to: $to from: $from" }
        logger.info { "Body: ${response.getBody()}" }
        logger.info { "Headers: ${response.getHeaders()}" }
      }

    } catch (ex: IOException) {
      throw ex
    }
  }
}