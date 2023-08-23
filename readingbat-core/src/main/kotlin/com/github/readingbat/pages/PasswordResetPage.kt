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

package com.github.readingbat.pages

import com.github.readingbat.common.Constants.LABEL_WIDTH
import com.github.readingbat.common.CssNames.INDENT_1EM
import com.github.readingbat.common.Endpoints.PASSWORD_CHANGE_ENDPOINT
import com.github.readingbat.common.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.EMAIL_PARAM
import com.github.readingbat.common.FormFields.NEW_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.github.readingbat.common.FormFields.RESET_ID_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_PASSWORD
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.hideShowButton
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.privacyStatement
import com.github.readingbat.posts.PasswordResetPost.ResetPasswordException
import com.github.readingbat.server.Email
import com.github.readingbat.server.PasswordResetsTable
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ResetId
import com.github.readingbat.server.ServerUtils.queryParam
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.two.KLogging
import org.jetbrains.exposed.sql.select
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.Seconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal object PasswordResetPage : KLogging() {

  private const val FORM_NAME = "pform"
  private const val PASSWORD_BUTTON = "UpdatePasswordButton"

  fun PipelineCall.passwordResetPage(resetId: ResetId, msg: Message = EMPTY_MESSAGE) =
    if (resetId.isBlank())
      requestPasswordResetPage(msg)
    else {
      try {
        val email =
          readonlyTx {
            val idAndUpdate =
              PasswordResetsTable
                .slice(PasswordResetsTable.email, PasswordResetsTable.updated)
                .select { PasswordResetsTable.resetId eq resetId.value }
                .map { it[0] as String to it[1] as DateTime }
                .firstOrNull() ?: throw ResetPasswordException("Invalid reset id. Try again.")

            Seconds.secondsBetween(idAndUpdate.second, now()).seconds.seconds
              .let { diff ->
                if (diff >= 15.minutes)
                  throw ResetPasswordException("Password reset must be completed within 15 mins ($diff). Try again.")
                else
                  idAndUpdate.first
              }
          }

        changePasswordPage(Email(email), resetId, msg)
      } catch (e: ResetPasswordException) {
        logger.info { e }
        requestPasswordResetPage(Message(e.message ?: "Unable to reset password", true))
      }
    }

  private fun PipelineCall.requestPasswordResetPage(msg: Message = EMPTY_MESSAGE) =
    createHTML()
      .html {
        head { headDefault() }

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
                  td { textInput { name = EMAIL_PARAM; size = "50" } }
                }
                tr {
                  td { }
                  td {
                    style = "padding-top:10"
                    submitInput {
                      style = "font-size:25px; height:35; width:  155"
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

          privacyStatement(PASSWORD_RESET_ENDPOINT)
          backLink(returnPath)
          loadPingdomScript()
        }
      }

  private fun PipelineCall.changePasswordPage(email: Email, resetId: ResetId, msg: Message) =
    createHTML()
      .html {
        head {
          headDefault()
          clickButtonScript(PASSWORD_BUTTON)
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          bodyTitle()

          p { span { style = "color:red"; this@body.displayMessage(msg) } }

          h3 { +"Change password for $email" }
          p { +"Password must contain at least 6 characters" }
          form {
            name = FORM_NAME
            action = PASSWORD_CHANGE_ENDPOINT
            method = FormMethod.post
            table {
              tr {
                td { style = LABEL_WIDTH; label { +"New Password" } }
                td { passwordInput { size = "42"; name = NEW_PASSWORD_PARAM; value = "" } }
                td { hideShowButton(FORM_NAME, NEW_PASSWORD_PARAM) }
              }

              tr {
                td { style = LABEL_WIDTH; label { +"Confirm Password" } }
                td {
                  passwordInput {
                    size = "42"
                    name = CONFIRM_PASSWORD_PARAM
                    value = ""
                    onKeyPress = "click$PASSWORD_BUTTON(event)"
                  }
                }
                td { hideShowButton(FORM_NAME, CONFIRM_PASSWORD_PARAM) }
              }

              tr {
                td { hiddenInput { name = RESET_ID_PARAM; value = resetId.value } }
                td {
                  submitInput {
                    style = "font-size:25px; height:35; width:155"
                    id = PASSWORD_BUTTON
                    name = PREFS_ACTION_PARAM
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