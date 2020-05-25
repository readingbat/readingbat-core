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
import java.io.IOException


object Emailer {

  fun sendResetEmail(toEmail: String) {
    val from = Email("reset@readingbat.com")
    val subject = "ReadingBat password reset"
    val to = Email(toEmail)
    val content = Content("text/plain", "and easy to do anywhere, even with Java")
    val mail = Mail(from, subject, to, content)
    val sg = SendGrid(System.getenv("SENDGRID_API_KEY"))
    try {
      val request =
        Request().apply {
          setMethod(Method.POST)
          setEndpoint("mail/send")
          setBody(mail.build())
        }

      val response: Response = sg.api(request)

      System.out.println(response.getStatusCode())
      System.out.println(response.getBody())
      System.out.println(response.getHeaders())

    } catch (ex: IOException) {
      throw ex
    }
  }
}