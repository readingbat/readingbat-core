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

import com.github.readingbat.common.Endpoints.EMAIL_CSS_FILE_PATH
import kotlinx.html.BODY
import kotlinx.html.FlowOrMetaDataContent
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.unsafe

object EmailUtils {
  fun readResourceFile(filename: String): String {
    val classLoader = this::class.java.classLoader
    return classLoader.getResource(filename)?.readText()
      ?: throw IllegalArgumentException("Invalid file name: $filename")
  }

  fun email(block: BODY.() -> Unit) =
    createHTML()
      .html {
        emailHead()
        body {
          block()
        }
      }

  fun HTML.emailHead() {
    head {
      embedCss()
      setBackground()
    }
  }

  fun FlowOrMetaDataContent.embedCss() {
    style {
      unsafe {
        raw(readResourceFile(EMAIL_CSS_FILE_PATH))
      }
    }
  }

  fun FlowOrMetaDataContent.setBackground(color: String = "light") {
    meta {
      name = "color-scheme"
      content = color
    }
    meta {
      name = "supported-color-schemes"
      content = color
    }
  }

//  fun sendVerificationEmail(
//    uuid: Uuid,
//    recipient: Email,
//  ) {
//    sendEmail(
//      from = envResendSender,
//      to = listOf(recipient),
//      subject = "$envSchoolName Student Load Planning Email Verification",
//      html = createHTML()
//        .html {
//          body {
//            h2 { +"Welcome to the $envSchoolName Student Load Planner" }
//            p { +"Please click the link below to verify your email address and activate your account." }
//            p { a("$envLoginUrl/$VERIFY_USER?uuid=$uuid") { +"Verify Email" } }
//          }
//        },
//    )
//  }

//  fun sendChangePasswordEmail(
//    recipient: Email,
//    userId: UserId,
//    secret: String,
//  ) {
//    sendEmail(
//      from = envResendSender,
//      to = listOf(recipient),
//      subject = "Student Load Planning Password Change",
//      html = createHTML()
//        .html {
//          body {
//            h2 { +"Student Load Planner Password Change" }
//            p { +"Please click the link below to change your password." }
//            p {
//              val param = encode(recipient.value, "utf-8")
//              val args = "$CHANGE_PASSWORD_PARAM=$userId&$SECRET_PARAM=$secret&$EMAIL_PARAM=$param"
//              a("$envLoginUrl/${CHANGE_PASSWORD_ENDPOINT.path}?$args") { +"Change Password" }
//            }
//          }
//        },
//    )
//  }
}
