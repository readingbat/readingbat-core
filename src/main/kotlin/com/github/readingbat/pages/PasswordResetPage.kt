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
import com.github.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.github.readingbat.common.FormFields.RESET_ID_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_PASSWORD
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.PasswordResets
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.hideShowButton
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.privacyStatement
import com.github.readingbat.posts.PasswordResetPost.ResetPasswordException
import com.github.readingbat.server.*
import com.github.readingbat.server.ReadingBatServer.usePostgres
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.Seconds
import redis.clients.jedis.Jedis
import kotlin.time.minutes
import kotlin.time.seconds

internal object PasswordResetPage : KLogging() {

  private const val formName = "pform"
  private const val passwordButton = "UpdatePasswordButton"

  fun PipelineCall.passwordResetPage(content: ReadingBatContent,
                                     resetId: ResetId,
                                     redis: Jedis,
                                     msg: Message = EMPTY_MESSAGE) =
    if (resetId.isBlank())
      requestPasswordResetPage(content, msg)
    else {
      try {
        val email =
          if (usePostgres)
            transaction {
              val idAndUpdate =
                PasswordResets
                  .slice(PasswordResets.email, PasswordResets.updated)
                  .select { PasswordResets.resetId eq resetId.value }
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
          else {
            val passwordResetKey = resetId.passwordResetKey
            redis.get(passwordResetKey) ?: throw ResetPasswordException(INVALID_RESET_ID)
          }

        changePasswordPage(content, Email(email), resetId, msg)

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
                td { passwordInput { size = "42"; name = NEW_PASSWORD_PARAM; value = "" } }
                td { hideShowButton(formName, NEW_PASSWORD_PARAM) }
              }

              tr {
                td { style = LABEL_WIDTH; label { +"Confirm Password" } }
                td {
                  passwordInput {
                    size = "42"
                    name = CONFIRM_PASSWORD_PARAM
                    value = ""
                    onKeyPress = "click$passwordButton(event)"
                  }
                }
                td { hideShowButton(formName, CONFIRM_PASSWORD_PARAM) }
              }

              tr {
                td { hiddenInput { name = RESET_ID_PARAM; value = resetId.value } }
                td {
                  submitInput {
                    style = "font-size:25px; height:35; width:155"
                    id = passwordButton
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