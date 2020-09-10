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

package com.github.readingbat.pages

import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.Constants.INVALID_RESET_ID
import com.github.readingbat.common.Constants.LABEL_WIDTH
import com.github.readingbat.common.Endpoints.PASSWORD_CHANGE_ENDPOINT
import com.github.readingbat.common.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.EMAIL_PARAM
import com.github.readingbat.common.FormFields.NEW_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.RESET_ID_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_PASSWORD
import com.github.readingbat.common.FormFields.USER_PREFS_ACTION_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.hideShowButton
import com.github.readingbat.pages.PageUtils.privacyStatement
import com.github.readingbat.posts.PasswordResetPost.ResetPasswordException
import com.github.readingbat.server.Email
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ResetId
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.onKeyPress
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import mu.KLogging
import redis.clients.jedis.Jedis

internal object PasswordResetPage : KLogging() {

  private const val formName = "pform"
  private const val passwordButton = "UpdatePasswordButton"

  fun PipelineCall.passwordResetPage(content: ReadingBatContent,
                                     resetId: ResetId,
                                     redis: Jedis,
                                     msg: Message = EMPTY_MESSAGE): String =
    if (resetId.isBlank())
      requestPasswordResetPage(content, msg)
    else {
      try {
        val passwordResetKey = resetId.passwordResetKey
        val email = Email(redis.get(passwordResetKey) ?: throw ResetPasswordException(INVALID_RESET_ID))

        changePasswordPage(content, email, resetId, msg)

      } catch (e: ResetPasswordException) {
        logger.info { e }
        requestPasswordResetPage(content, Message(e.message ?: "Unable to reset password", true))
      }
    }

  private fun PipelineCall.requestPasswordResetPage(content: ReadingBatContent,
                                                    msg: Message = EMPTY_MESSAGE) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          bodyTitle()

          p { span { style = "color:red"; this@body.displayMessage(msg) } }

          h2 { +"Password Reset" }

          div(classes = INDENT_1EM) {
            form {
              action = "$PASSWORD_RESET_ENDPOINT?$RETURN_PARAM=$returnPath"
              method = FormMethod.post
              table {
                tr {
                  td { style = LABEL_WIDTH; label { +"Email (used as account id)" } }
                  td { input { name = EMAIL_PARAM; type = InputType.text; size = "50" } }
                }
                tr {
                  td { }
                  td {
                    style = "padding-top:10"
                    input {
                      style = "font-size:25px; height:35; width:  155"
                      type = InputType.submit
                      value = "Send Password Reset"
                    }
                  }
                }
              }
            }
            p {
              +"""
                This will send an email with a link that will allow you to set your password. 
                When you get the email, click on the link (or copy and paste the URL into your browser's address bar) 
                and enter a new password. If the email does not arrive, double-check that the email 
                address above is entered correctly.
              """.trimIndent()
            }
          }

          privacyStatement(PASSWORD_RESET_ENDPOINT, returnPath)

          backLink(returnPath)
        }
      }

  private fun PipelineCall.changePasswordPage(content: ReadingBatContent,
                                              email: Email,
                                              resetId: ResetId,
                                              msg: Message) =
    createHTML()
      .html {
        head {
          headDefault(content)
          clickButtonScript(passwordButton)
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          bodyTitle()

          p { span { style = "color:red"; this@body.displayMessage(msg) } }

          h3 { +"Change password for $email" }
          p { +"Password must contain at least 6 characters" }
          form {
            name = formName
            action = PASSWORD_CHANGE_ENDPOINT
            method = FormMethod.post
            table {
              tr {
                td { style = LABEL_WIDTH; label { +"New Password" } }
                td { input { type = InputType.password; size = "42"; name = NEW_PASSWORD_PARAM; value = "" } }
                td { hideShowButton(formName, NEW_PASSWORD_PARAM) }
              }

              tr {
                td { style = LABEL_WIDTH; label { +"Confirm Password" } }
                td {
                  input {
                    type = InputType.password
                    size = "42"
                    name = CONFIRM_PASSWORD_PARAM
                    value = ""
                    onKeyPress = "click$passwordButton(event)"
                  }
                }
                td { hideShowButton(formName, CONFIRM_PASSWORD_PARAM) }
              }

              tr {
                td { input { type = InputType.hidden; name = RESET_ID_PARAM; value = resetId.value } }
                td {
                  input {
                    style = "font-size:25px; height:35; width:155"
                    type = InputType.submit
                    id = passwordButton
                    name = USER_PREFS_ACTION_PARAM
                    value = UPDATE_PASSWORD
                  }
                }
              }
            }
          }

          backLink(returnPath)
        }
      }
}